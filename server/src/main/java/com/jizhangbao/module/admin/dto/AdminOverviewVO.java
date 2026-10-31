package com.jizhangbao.module.admin.dto;

import java.math.BigDecimal;

/**
 * 后台概览数据。
 */
public record AdminOverviewVO(
        long userCount,
        long activeUserCount,
        long billCount,
        long categoryCount,
        BigDecimal totalExpense,
        BigDecimal totalIncome,
        long reportCount,
        long aiCallCount,
        long todayAiCallCount,
        long todayNewUsers
) {
}
