package com.jizhangbao.module.report.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jizhangbao.common.exception.BizException;
import com.jizhangbao.common.exception.ErrorCode;
import com.jizhangbao.common.util.CacheHelper;
import com.jizhangbao.common.util.CacheKeys;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.module.ai.dto.AnalysisVO;
import com.jizhangbao.module.ai.service.AiAnalysisService;
import com.jizhangbao.module.report.dto.MonthlyReportVO;
import com.jizhangbao.module.report.entity.MonthlyReport;
import com.jizhangbao.module.report.mapper.MonthlyReportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 月度消费分析报告：生成、缓存、查询。
 */
@Service
public class MonthlyReportService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportService.class);

    private final MonthlyReportMapper reportMapper;
    private final AiAnalysisService analysisService;
    private final ObjectMapper objectMapper;
    private final CacheHelper cacheHelper;

    public MonthlyReportService(MonthlyReportMapper reportMapper, AiAnalysisService analysisService,
                                ObjectMapper objectMapper, CacheHelper cacheHelper) {
        this.reportMapper = reportMapper;
        this.analysisService = analysisService;
        this.objectMapper = objectMapper;
        this.cacheHelper = cacheHelper;
    }

    /**
     * 获取某月报告，不存在则实时生成。
     */
    public MonthlyReportVO getOrGenerate(Long userId, String yearMonth) {
        String month = normalize(yearMonth);
        MonthlyReportVO existing = find(userId, month);
        if (existing != null) {
            return existing;
        }
        return generate(userId, month);
    }

    /**
     * 强制重新生成。
     */
    @Transactional
    public MonthlyReportVO generate(Long userId, String yearMonth) {
        String month = normalize(yearMonth);
        // 生成锁，避免并发重复生成
        String lockKey = CacheKeys.reportLock(userId, month);
        boolean locked = cacheHelper.setIfAbsent(lockKey, Duration.ofSeconds(30));
        try {
            AnalysisVO analysis = analysisService.analyze(userId, month);
            if (analysis.billCount() == null || analysis.billCount() == 0) {
                throw new BizException(ErrorCode.REPORT_EMPTY_DATA);
            }
            String topCategory = analysis.insights().stream()
                    .findFirst()
                    .map(AnalysisVO.Item::title)
                    .orElse(null);

            MonthlyReport report = reportMapper.selectOne(new LambdaQueryWrapper<MonthlyReport>()
                    .eq(MonthlyReport::getUserId, userId)
                    .eq(MonthlyReport::getYearMonth, month)
                    .last("LIMIT 1"));
            boolean isNew = report == null;
            if (isNew) {
                report = new MonthlyReport();
                report.setUserId(userId);
                report.setYearMonth(month);
            }
            report.setTotalIncome(analysis.totalIncome());
            report.setTotalExpense(analysis.totalExpense());
            report.setBalance(analysis.balance());
            report.setBillCount(analysis.billCount() == null ? 0 : analysis.billCount().intValue());
            report.setTopCategory(topCategory);
            report.setContent(renderMarkdown(analysis));
            report.setInsights(writeJson(analysis.insights()));
            report.setSuggestions(writeJson(analysis.suggestions()));
            report.setGenerator(analysis.generator());
            if (isNew) {
                reportMapper.insert(report);
            } else {
                reportMapper.updateById(report);
            }
            log.info("月度报告已生成 userId={} month={} generator={}", userId, month, analysis.generator());
            return toVO(report);
        } finally {
            if (locked) {
                cacheHelper.delete(lockKey);
            }
        }
    }

    public List<MonthlyReportVO> list(Long userId, int limit) {
        return reportMapper.selectList(new LambdaQueryWrapper<MonthlyReport>()
                        .eq(MonthlyReport::getUserId, userId)
                        .orderByDesc(MonthlyReport::getYearMonth)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 36))))
                .stream()
                .map(this::toVO)
                .toList();
    }

    public MonthlyReportVO find(Long userId, String yearMonth) {
        MonthlyReport report = reportMapper.selectOne(new LambdaQueryWrapper<MonthlyReport>()
                .eq(MonthlyReport::getUserId, userId)
                .eq(MonthlyReport::getYearMonth, normalize(yearMonth))
                .last("LIMIT 1"));
        return report == null ? null : toVO(report);
    }

    /* ==================== 内部方法 ==================== */

    private String renderMarkdown(AnalysisVO analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(analysis.yearMonth()).append(" 消费分析\n\n");
        sb.append(analysis.summary()).append("\n\n");
        if (!analysis.insights().isEmpty()) {
            sb.append("### 关键发现\n");
            for (AnalysisVO.Item item : analysis.insights()) {
                sb.append("- **").append(item.title()).append("**：").append(item.detail());
                if (item.evidence() != null && !item.evidence().isBlank()) {
                    sb.append("（").append(item.evidence()).append("）");
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
        if (!analysis.suggestions().isEmpty()) {
            sb.append("### 省钱建议\n");
            for (AnalysisVO.Item item : analysis.suggestions()) {
                sb.append("- **").append(item.title()).append("**：").append(item.detail()).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String writeJson(List<AnalysisVO.Item> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<AnalysisVO.Item> readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AnalysisVO.Item>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private MonthlyReportVO toVO(MonthlyReport report) {
        return new MonthlyReportVO(
                report.getId(),
                report.getYearMonth(),
                report.getTotalIncome() == null ? BigDecimal.ZERO : report.getTotalIncome(),
                report.getTotalExpense() == null ? BigDecimal.ZERO : report.getTotalExpense(),
                report.getBalance() == null ? BigDecimal.ZERO : report.getBalance(),
                report.getBillCount() == null ? 0 : report.getBillCount(),
                report.getTopCategory(),
                report.getContent(),
                readJson(report.getInsights()),
                readJson(report.getSuggestions()),
                report.getGenerator(),
                report.getCreatedAt()
        );
    }

    private String normalize(String yearMonth) {
        return (yearMonth == null || yearMonth.isBlank()) ? DateUtils.currentMonth()
                : DateUtils.parseMonth(yearMonth).format(DateUtils.YM);
    }

    /** 供定时任务使用：按月遍历所有用户 */
    public List<String> monthsOfYear(int year) {
        return DateUtils.monthsBetween(year + "-01", year + "-12");
    }

    public static String previousMonth() {
        return LocalDate.now().minusMonths(1).format(DateUtils.YM);
    }
}
