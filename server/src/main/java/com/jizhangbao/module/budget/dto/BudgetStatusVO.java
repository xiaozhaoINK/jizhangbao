package com.jizhangbao.module.budget.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 某月预算总览 + 超支提醒。
 */
public record BudgetStatusVO(
        String yearMonth,
        BigDecimal totalBudget,
        BigDecimal totalUsed,
        BigDecimal totalRemaining,
        BigDecimal usageRate,
        BigDecimal dailyAllowance,
        Integer daysLeft,
        List<BudgetVO> items,
        List<Alert> alerts
) {
    /**
     * 超支 / 临界提醒。
     */
    public record Alert(
            String level,
            String title,
            String message,
            Long categoryId,
            BigDecimal amount,
            BigDecimal usageRate
    ) {
    }
}
