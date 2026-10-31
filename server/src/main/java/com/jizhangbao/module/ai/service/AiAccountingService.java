package com.jizhangbao.module.ai.service;

import com.jizhangbao.common.enums.BillType;
import com.jizhangbao.common.exception.BizException;
import com.jizhangbao.common.exception.ErrorCode;
import com.jizhangbao.common.util.CacheHelper;
import com.jizhangbao.common.util.CacheKeys;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.module.ai.dto.BillParseVO;
import com.jizhangbao.module.ai.engine.AiEngine;
import com.jizhangbao.module.ai.entity.AiUsageLog;
import com.jizhangbao.module.ai.mapper.AiUsageLogMapper;
import com.jizhangbao.module.bill.dto.BillVO;
import com.jizhangbao.module.bill.entity.Bill;
import com.jizhangbao.module.bill.service.BillService;
import com.jizhangbao.module.category.entity.Category;
import com.jizhangbao.module.category.service.CategoryService;
import com.jizhangbao.module.category.support.CategoryMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 记账服务：自然语言 → 结构化账单 → 一键落账。
 *
 * <p>三条工程约束：</p>
 * <ol>
 *   <li><b>限流</b>：每个用户每分钟最多 20 次解析，用 Redis 计数器实现；</li>
 *   <li><b>缓存</b>：同一句话 1 小时内重复解析直接命中缓存，既省钱又更快；</li>
 *   <li><b>降级</b>：大模型异常时自动回退到规则引擎，记账入口不会因为 AI 故障而不可用。</li>
 * </ol>
 */
@Service
public class AiAccountingService {

    private static final Logger log = LoggerFactory.getLogger(AiAccountingService.class);

    private static final int RATE_LIMIT_PER_MINUTE = 20;

    private final AiEngine aiEngine;
    private final CategoryService categoryService;
    private final BillService billService;
    private final CacheHelper cacheHelper;
    private final AiUsageLogMapper usageLogMapper;

    public AiAccountingService(AiEngine aiEngine, CategoryService categoryService,
                               BillService billService, CacheHelper cacheHelper,
                               AiUsageLogMapper usageLogMapper) {
        this.aiEngine = aiEngine;
        this.categoryService = categoryService;
        this.billService = billService;
        this.cacheHelper = cacheHelper;
        this.usageLogMapper = usageLogMapper;
    }

    /**
     * 解析自然语言，返回可供用户确认的结构化账单。
     */
    public BillParseVO parse(Long userId, String text) {
        String input = text == null ? "" : text.trim();
        if (!StringUtils.hasText(input)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请输入要记账的内容");
        }
        checkRateLimit(userId);

        String cacheKey = CacheKeys.aiParse(userId,
                DigestUtils.md5DigestAsHex(input.getBytes(StandardCharsets.UTF_8)));
        return cacheHelper.rememberJson(cacheKey, Duration.ofHours(1),
                new com.fasterxml.jackson.core.type.TypeReference<BillParseVO>() {
                },
                () -> doParse(userId, input));
    }

    private BillParseVO doParse(Long userId, String input) {
        List<Category> categories = categoryService.listEntity(userId);
        List<Category> expenses = categories.stream()
                .filter(c -> BillType.EXPENSE.name().equalsIgnoreCase(c.getType())).toList();
        List<Category> incomes = categories.stream()
                .filter(c -> BillType.INCOME.name().equalsIgnoreCase(c.getType())).toList();

        long start = System.currentTimeMillis();
        AiEngine.ParsedBill parsed;
        String usedEngine = aiEngine.name();
        try {
            parsed = aiEngine.parseBill(new AiEngine.ParseInput(input, expenses, incomes));
        } catch (Exception e) {
            log.warn("AI 解析失败，降级为本地规则引擎: {}", e.getMessage());
            usedEngine = "RULE(fallback)";
            parsed = localFallback(input, expenses, incomes);
        }
        long cost = System.currentTimeMillis() - start;

        BillParseVO vo = toVO(userId, input, parsed, usedEngine, categories);
        logUsage(userId, "PARSE", usedEngine, input.length(), true, cost);
        return vo;
    }

    /**
     * 解析并直接落账（AI 一键记账）。
     */
    @Transactional
    public BillVO record(Long userId, String text) {
        BillParseVO parsed = parse(userId, text);
        if (parsed.amount() == null) {
            throw new BizException(ErrorCode.AI_AMOUNT_MISSING);
        }
        if (parsed.categoryId() == null) {
            throw new BizException(ErrorCode.AI_PARSE_FAILED, "没有匹配到合适的分类，请先完善分类或手动记账");
        }
        Bill bill = billService.createFromAi(userId, parsed.categoryId(), parsed.type(), parsed.amount(),
                parsed.billDate(), parsed.remark(), parsed.payMethod(), parsed.rawText());
        cacheHelper.delete(CacheKeys.aiParse(userId,
                DigestUtils.md5DigestAsHex(text.trim().getBytes(StandardCharsets.UTF_8))));
        return billService.toVOList(List.of(bill), userId).get(0);
    }

    /** 解析结果的分类兜底：用户没有合适分类时归到「其他」 */
    private BillParseVO toVO(Long userId, String input, AiEngine.ParsedBill parsed, String engine,
                             List<Category> allCategories) {
        String type = BillType.INCOME.name().equalsIgnoreCase(parsed.type())
                ? BillType.INCOME.name() : BillType.EXPENSE.name();
        List<Category> candidates = allCategories.stream()
                .filter(c -> type.equalsIgnoreCase(c.getType())).toList();

        Category category = null;
        if (StringUtils.hasText(parsed.categoryName())) {
            category = CategoryMatcher.matchByName(candidates, parsed.categoryName());
            if (category == null) {
                // 大模型可能给出一个更口语的说法，再走一次关键词匹配
                category = CategoryMatcher.match(candidates, parsed.categoryName() + " " + input);
            }
        }
        if (category == null) {
            category = CategoryMatcher.match(candidates, input);
        }
        if (category == null) {
            category = candidates.stream()
                    .filter(c -> c.getName().contains("其他"))
                    .findFirst()
                    .orElse(candidates.isEmpty() ? null : candidates.get(0));
        }

        Category parent = null;
        if (category != null && category.getParentId() != null && category.getParentId() > 0) {
            Long parentId = category.getParentId();
            parent = allCategories.stream().filter(c -> c.getId().equals(parentId)).findFirst().orElse(null);
        }

        List<String> hints = new ArrayList<>();
        if (parsed.amount() == null) {
            hints.add("没有识别到金额，请手动填写");
        }
        if (category == null) {
            hints.add("没有匹配到分类，请手动选择");
        }
        if (parent != null && parent.getName().contains("其他")) {
            hints.add("自动归入了「其他」，可以改成更准确的分类");
        }

        return new BillParseVO(
                type,
                BillType.of(type).label(),
                parsed.amount(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName(),
                category == null ? "other" : category.getIcon(),
                category == null ? "#8C8C8C" : category.getColor(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getName(),
                parsed.billDate() == null ? LocalDate.now() : parsed.billDate(),
                DateUtils.humanDate(parsed.billDate() == null ? LocalDate.now() : parsed.billDate()),
                parsed.remark(),
                parsed.payMethod(),
                Math.round(parsed.confidence() * 100) / 100.0,
                engine,
                parsed.amount() != null && category != null,
                input,
                hints
        );
    }

    private AiEngine.ParsedBill localFallback(String input, List<Category> expenses, List<Category> incomes) {
        Category expenseCategory = CategoryMatcher.match(expenses, input);
        Category incomeCategory = CategoryMatcher.match(incomes, input);
        Category picked = incomeCategory != null && expenseCategory == null ? incomeCategory : expenseCategory;
        String type = picked != null && BillType.INCOME.name().equalsIgnoreCase(picked.getType())
                ? BillType.INCOME.name() : BillType.EXPENSE.name();
        return new AiEngine.ParsedBill(type, extractAmount(input), picked == null ? null : picked.getName(),
                LocalDate.now(), input, null, 0.4);
    }

    private java.math.BigDecimal extractAmount(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d{1,2})?)").matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate.length() == 4 && candidate.startsWith("20")) {
                continue;
            }
            return new java.math.BigDecimal(candidate);
        }
        return null;
    }

    private void checkRateLimit(Long userId) {
        String minute = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmm")
                .format(java.time.LocalDateTime.now());
        long count = cacheHelper.increment(CacheKeys.aiRateLimit(userId, minute), Duration.ofMinutes(2));
        if (count > RATE_LIMIT_PER_MINUTE) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, "AI 解析太频繁了，休息一下再试");
        }
    }

    private void logUsage(Long userId, String scene, String engine, int chars, boolean success, long costMs) {
        try {
            AiUsageLog logEntity = new AiUsageLog();
            logEntity.setUserId(userId);
            logEntity.setScene(scene);
            logEntity.setEngine(engine);
            logEntity.setInputChars(chars);
            logEntity.setSuccess(success ? 1 : 0);
            logEntity.setCostMs((int) Math.min(costMs, Integer.MAX_VALUE));
            usageLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.debug("AI 调用日志写入失败: {}", e.getMessage());
        }
    }
}
