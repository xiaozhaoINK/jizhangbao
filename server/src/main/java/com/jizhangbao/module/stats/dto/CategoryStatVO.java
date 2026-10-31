package com.jizhangbao.module.stats.dto;

import java.math.BigDecimal;

/**
 * 分类占比数据。
 */
public record CategoryStatVO(
        Long categoryId,
        String name,
        String icon,
        String color,
        BigDecimal amount,
        Long count,
        BigDecimal percent,
        BigDecimal avgAmount
) {
}
