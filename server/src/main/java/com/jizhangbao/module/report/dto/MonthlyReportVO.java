package com.jizhangbao.module.report.dto;

import com.jizhangbao.module.ai.dto.AnalysisVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 月度报告视图。
 */
public record MonthlyReportVO(
        Long id,
        String yearMonth,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        Integer billCount,
        String topCategory,
        String content,
        List<AnalysisVO.Item> insights,
        List<AnalysisVO.Item> suggestions,
        String generator,
        LocalDateTime createdAt
) {
}
