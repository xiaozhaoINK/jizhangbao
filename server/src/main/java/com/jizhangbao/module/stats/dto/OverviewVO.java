package com.jizhangbao.module.stats.dto;

import java.math.BigDecimal;

/**
 * 首页 / 工作台概览。
 */
public record OverviewVO(
        String yearMonth,
        /* 本月 */
        BigDecimal monthIncome,
        BigDecimal monthExpense,
        BigDecimal monthBalance,
        Long monthBillCount,
        /* 今日 */
        BigDecimal todayIncome,
        BigDecimal todayExpense,
        Long todayBillCount,
        /* 上月对比 */
        BigDecimal lastMonthIncome,
        BigDecimal lastMonthExpense,
        BigDecimal expenseMoM,
        BigDecimal incomeMoM,
        /* 日均与结构 */
        BigDecimal avgDailyExpense,
        String topCategoryName,
        BigDecimal topCategoryAmount,
        /* 预算 */
        BigDecimal budgetAmount,
        BigDecimal budgetUsed,
        BigDecimal budgetUsageRate,
        /* 连续记账天数 */
        Integer continuousDays
) {
}
