package com.jizhangbao.module.stats.dto;

import java.math.BigDecimal;

/**
 * 趋势图数据点。
 */
public record TrendPointVO(
        String date,
        String label,
        BigDecimal income,
        BigDecimal expense
) {
}
