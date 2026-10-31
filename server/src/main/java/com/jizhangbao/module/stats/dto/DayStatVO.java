package com.jizhangbao.module.stats.dto;

import java.math.BigDecimal;

/**
 * 每日收支（日历 / 热力图）。
 */
public record DayStatVO(
        String date,
        Integer day,
        BigDecimal income,
        BigDecimal expense,
        Long count,
        Integer level
) {
}
