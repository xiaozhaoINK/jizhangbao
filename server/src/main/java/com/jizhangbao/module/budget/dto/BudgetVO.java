package com.jizhangbao.module.budget.dto;

import java.math.BigDecimal;

/**
 * 单条预算的执行情况。
 */
public record BudgetVO(
        Long id,
        String yearMonth,
        Long categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        BigDecimal amount,
        BigDecimal used,
        BigDecimal remaining,
        BigDecimal usageRate,
        Integer alertThreshold,
        /** SAFE / WARNING / EXCEEDED */
        String status,
        BigDecimal dailyAllowance,
        Integer daysLeft
) {
}
