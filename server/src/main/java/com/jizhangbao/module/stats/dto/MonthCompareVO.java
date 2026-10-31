package com.jizhangbao.module.stats.dto;

import java.math.BigDecimal;

/**
 * 月度对比数据点。
 */
public record MonthCompareVO(
        String month,
        String label,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance
) {
}
