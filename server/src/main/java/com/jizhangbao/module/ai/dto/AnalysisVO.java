package com.jizhangbao.module.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 消费洞察 / 月度分析结果。
 */
public record AnalysisVO(
        String yearMonth,
        String title,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal balance,
        Long billCount,
        String summary,
        List<Item> insights,
        List<Item> suggestions,
        String generator
) {
    /**
     * 一条洞察或建议。
     *
     * @param type     INFO / WARNING / GOOD / TIP
     * @param title    标题
     * @param detail   说明
     * @param evidence 支撑数据（如「餐饮 2,340 元，占比 38%」）
     */
    public record Item(String type, String title, String detail, String evidence) {
    }
}
