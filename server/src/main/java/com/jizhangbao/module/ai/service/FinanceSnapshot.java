package com.jizhangbao.module.ai.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户某个时间片的账本快照：既给大模型拼装上下文，也给本地规则问答直接使用。
 */
public record FinanceSnapshot(
        String yearMonth,
        BigDecimal monthIncome,
        BigDecimal monthExpense,
        BigDecimal balance,
        long billCount,
        BigDecimal lastMonthIncome,
        BigDecimal lastMonthExpense,
        BigDecimal expenseMoM,
        BigDecimal avgDailyExpense,
        int continuousDays,
        List<CategoryLine> topExpenseCategories,
        List<CategoryLine> topIncomeCategories,
        BigDecimal budgetAmount,
        BigDecimal budgetUsed,
        BigDecimal budgetUsageRate,
        String topSingleBill,
        /** 渲染好的、可直接塞进提示词的中文上下文 */
        String text
) {

    public record CategoryLine(String name, BigDecimal amount, BigDecimal percent, long count) {
    }

    /** 渲染上下文之后的副本 */
    public FinanceSnapshot withText(String renderedText) {
        return new FinanceSnapshot(yearMonth, monthIncome, monthExpense, balance, billCount,
                lastMonthIncome, lastMonthExpense, expenseMoM, avgDailyExpense, continuousDays,
                topExpenseCategories, topIncomeCategories, budgetAmount, budgetUsed,
                budgetUsageRate, topSingleBill, renderedText);
    }
}
