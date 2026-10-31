package com.jizhangbao.module.bill.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账单视图。
 */
public record BillVO(
        Long id,
        String type,
        Long categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        Long parentCategoryId,
        String parentCategoryName,
        BigDecimal amount,
        String remark,
        LocalDate billDate,
        LocalDateTime billTime,
        String payMethod,
        String tags,
        String source,
        String aiInput,
        LocalDateTime createdAt
) {
}
