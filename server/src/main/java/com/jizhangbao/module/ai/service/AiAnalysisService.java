package com.jizhangbao.module.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.module.ai.dto.AnalysisVO;
import com.jizhangbao.module.ai.engine.AiEngine;
import com.jizhangbao.module.ai.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费洞察服务：把账本数据变成「异常提示 + 变化趋势 + 可执行建议」。
 *
 * <p>大模型可用时由模型组织语言；不可用时用一组数据驱动的规则生成，保证洞察永远有料、且数字真实。</p>
 */
@Service
public class AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisService.class);

    private final AiEngine aiEngine;
    private final FinanceContextBuilder contextBuilder;
    private final PromptTemplates promptTemplates;
    private final ObjectMapper objectMapper;

    public AiAnalysisService(AiEngine aiEngine, FinanceContextBuilder contextBuilder,
                             PromptTemplates promptTemplates, ObjectMapper objectMapper) {
        this.aiEngine = aiEngine;
        this.contextBuilder = contextBuilder;
        this.promptTemplates = promptTemplates;
        this.objectMapper = objectMapper;
    }

    public AnalysisVO analyze(Long userId, String yearMonth) {
        String month = (yearMonth == null || yearMonth.isBlank()) ? DateUtils.currentMonth() : yearMonth;
        FinanceSnapshot snapshot = contextBuilder.build(userId, month);
        if (snapshot.billCount() == 0) {
            return new AnalysisVO(month, month + " 消费分析", BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0L,
                    "这个月还没有记账数据，先记上几笔，我就能帮你分析消费结构了。",
                    List.of(), List.of(), "RULE");
        }
        if (aiEngine.llm()) {
            try {
                Map<String, String> variables = new HashMap<>();
                variables.put("financeContext", snapshot.text());
                String systemPrompt = promptTemplates.render(PromptTemplates.ANALYSIS, variables);
                String content = aiEngine.complete(systemPrompt, "请输出本月的消费洞察 JSON。");
                AnalysisVO parsed = parseLlmResult(month, snapshot, content);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception e) {
                log.warn("大模型洞察生成失败，降级为规则引擎: {}", e.getMessage());
            }
        }
        return ruleBased(month, snapshot);
    }

    /* ==================== 大模型结果解析 ==================== */

    private AnalysisVO parseLlmResult(String month, FinanceSnapshot snapshot, String content) {
        if (content == null) {
            return null;
        }
        String cleaned = content.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(cleaned.substring(start, end + 1));
            List<AnalysisVO.Item> insights = readItems(root.path("insights"));
            List<AnalysisVO.Item> suggestions = readItems(root.path("suggestions"));
            String summary = root.path("summary").asText("本月消费情况已生成。");
            return new AnalysisVO(month, month + " 消费分析", snapshot.monthIncome(), snapshot.monthExpense(),
                    snapshot.balance(), snapshot.billCount(), summary, insights, suggestions, "LLM");
        } catch (Exception e) {
            log.warn("洞察 JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private List<AnalysisVO.Item> readItems(JsonNode node) {
        List<AnalysisVO.Item> items = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                items.add(new AnalysisVO.Item(
                        item.path("type").asText("INFO"),
                        item.path("title").asText(""),
                        item.path("detail").asText(""),
                        item.path("evidence").asText("")
                ));
            }
        }
        return items;
    }

    /* ==================== 规则引擎生成 ==================== */

    private AnalysisVO ruleBased(String month, FinanceSnapshot s) {
        List<AnalysisVO.Item> insights = new ArrayList<>();
        List<AnalysisVO.Item> suggestions = new ArrayList<>();

        // 1. 支出最高分类
        if (!s.topExpenseCategories().isEmpty()) {
            FinanceSnapshot.CategoryLine top = s.topExpenseCategories().get(0);
            String type = top.percent().compareTo(BigDecimal.valueOf(40)) >= 0 ? "WARNING" : "INFO";
            insights.add(new AnalysisVO.Item(type, "「" + top.name() + "」是最大支出项",
                    top.percent().compareTo(BigDecimal.valueOf(40)) >= 0
                            ? "占比超过四成，消费结构偏集中，是省钱的第一突破口。"
                            : "这是本月花得最多的类别，值得持续关注。",
                    top.name() + " " + money(top.amount()) + " 元，占比 " + pct(top.percent())
                            + "，共 " + top.count() + " 笔"));
        }

        // 2. 环比变化
        if (s.lastMonthExpense() != null && s.lastMonthExpense().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal mom = s.expenseMoM();
            if (mom.compareTo(BigDecimal.valueOf(20)) >= 0) {
                insights.add(new AnalysisVO.Item("WARNING", "支出环比明显上升",
                        "本月比上月多花了 " + money(s.monthExpense().subtract(s.lastMonthExpense()))
                                + " 元，建议回看是哪些笔数变多了。",
                        "本月 " + money(s.monthExpense()) + " 元，上月 " + money(s.lastMonthExpense())
                                + " 元，环比 +" + pct(mom)));
            } else if (mom.compareTo(BigDecimal.valueOf(-10)) <= 0) {
                insights.add(new AnalysisVO.Item("GOOD", "支出环比下降",
                        "比上月少花了 " + money(s.lastMonthExpense().subtract(s.monthExpense()))
                                + " 元，控制得不错。",
                        "本月 " + money(s.monthExpense()) + " 元，环比 " + pct(mom)));
            }
        }

        // 3. 预算执行
        if (s.budgetAmount().compareTo(BigDecimal.ZERO) > 0) {
            if (s.budgetUsageRate().compareTo(BigDecimal.valueOf(100)) >= 0) {
                insights.add(new AnalysisVO.Item("WARNING", "本月预算已超支",
                        "剩下的日子建议只保留必要支出。",
                        "预算 " + money(s.budgetAmount()) + " 元，已用 " + money(s.budgetUsed())
                                + " 元（" + pct(s.budgetUsageRate()) + "）"));
            } else if (s.budgetUsageRate().compareTo(BigDecimal.valueOf(80)) >= 0) {
                insights.add(new AnalysisVO.Item("WARNING", "预算接近上限",
                        "已使用超过八成，注意控制后半月节奏。",
                        "已用 " + pct(s.budgetUsageRate()) + "，剩余 "
                                + money(s.budgetAmount().subtract(s.budgetUsed())) + " 元"));
            } else {
                insights.add(new AnalysisVO.Item("GOOD", "预算执行健康",
                        "当前节奏可控，保持即可。",
                        "已用 " + pct(s.budgetUsageRate()) + "，剩余 "
                                + money(s.budgetAmount().subtract(s.budgetUsed())) + " 元"));
            }
        } else {
            insights.add(new AnalysisVO.Item("INFO", "还没有设置预算",
                    "设置预算后可以每天看到剩余额度，更容易控制超支。",
                    "本月支出 " + money(s.monthExpense()) + " 元"));
        }

        // 4. 储蓄率
        if (s.monthIncome().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal savingRate = s.balance().multiply(BigDecimal.valueOf(100))
                    .divide(s.monthIncome(), 2, RoundingMode.HALF_UP);
            insights.add(new AnalysisVO.Item(savingRate.compareTo(BigDecimal.valueOf(30)) >= 0 ? "GOOD" : "INFO",
                    "本月储蓄率 " + pct(savingRate),
                    savingRate.compareTo(BigDecimal.valueOf(30)) >= 0
                            ? "储蓄率超过 30%，财务状况健康。"
                            : "储蓄率还有提升空间，可以从压缩非必要消费开始。",
                    "收入 " + money(s.monthIncome()) + " 元，结余 " + money(s.balance()) + " 元"));
        }

        // 5. 最大单笔
        if (s.topSingleBill() != null) {
            insights.add(new AnalysisVO.Item("INFO", "本月最大单笔支出", "大额支出建议复盘是否必要。",
                    s.topSingleBill()));
        }

        // 建议：优先挑「能压缩」的类别（房租这类刚性支出不适合建议砍两成）
        FinanceSnapshot.CategoryLine savable = pickSavableCategory(s);
        if (savable != null) {
            BigDecimal save = savable.amount().multiply(BigDecimal.valueOf(0.2))
                    .setScale(2, RoundingMode.HALF_UP);
            suggestions.add(new AnalysisVO.Item("TIP", "压缩「" + savable.name() + "」两成",
                    "把 " + savable.name() + " 的支出降低 20%，下个月就能多存一笔。",
                    "本月 " + money(savable.amount()) + " 元 → 预计可省 " + money(save) + " 元/月"));
        }
        if (s.budgetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal suggested = s.monthExpense().compareTo(BigDecimal.valueOf(500)) > 0
                    ? s.monthExpense().multiply(BigDecimal.valueOf(0.9)).setScale(0, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(1000);
            suggestions.add(new AnalysisVO.Item("TIP", "设置月度预算",
                    "按本月支出打九折设预算，既有约束又不至于太紧。",
                    "建议预算 " + money(suggested) + " 元"));
        } else {
            BigDecimal remain = s.budgetAmount().subtract(s.budgetUsed()).max(BigDecimal.ZERO);
            BigDecimal daily = remain.divide(BigDecimal.valueOf(10), 2, RoundingMode.HALF_UP);
            suggestions.add(new AnalysisVO.Item("TIP", "给每天定一个消费上限",
                    "把剩余额度均摊到接下来的日子，单日不超线。",
                    "剩余 " + money(remain) + " 元 → 单日上限约 " + money(daily) + " 元"));
        }
        suggestions.add(new AnalysisVO.Item("TIP", "每周留一天「无消费日」",
                "固定一天不产生非必要支出，是最容易坚持的省钱习惯。",
                "按日均 " + money(s.avgDailyExpense()) + " 元计算，一月约省 "
                        + money(s.avgDailyExpense().multiply(BigDecimal.valueOf(4))) + " 元"));

        String summary = String.format("本月支出 %s 元，收入 %s 元，结余 %s 元，共 %d 笔%s。",
                money(s.monthExpense()), money(s.monthIncome()), money(s.balance()), s.billCount(),
                s.topExpenseCategories().isEmpty() ? ""
                        : "，支出大头是「" + s.topExpenseCategories().get(0).name() + "」");

        return new AnalysisVO(month, month + " 消费分析", s.monthIncome(), s.monthExpense(),
                s.balance(), s.billCount(), summary, insights, suggestions, "RULE");
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 刚性支出（房租/水电/还款等）不适合建议"砍两成"，这里挑一个可优化的类别 */
    private FinanceSnapshot.CategoryLine pickSavableCategory(FinanceSnapshot snapshot) {
        List<String> rigid = List.of("居住", "房租", "水电燃气", "物业费", "宽带通讯", "其他支出", "其他");
        for (FinanceSnapshot.CategoryLine line : snapshot.topExpenseCategories()) {
            if (!rigid.contains(line.name()) && line.amount().compareTo(BigDecimal.ZERO) > 0) {
                return line;
            }
        }
        return snapshot.topExpenseCategories().isEmpty() ? null : snapshot.topExpenseCategories().get(0);
    }

    private String pct(BigDecimal value) {
        return value == null ? "0%" : value.stripTrailingZeros().toPlainString() + "%";
    }
}
