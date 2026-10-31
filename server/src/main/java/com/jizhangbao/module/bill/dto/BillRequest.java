package com.jizhangbao.module.bill.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增 / 编辑账单请求。
 */
public record BillRequest(
        @NotNull(message = "请选择账单类型")
        @Pattern(regexp = "INCOME|EXPENSE", message = "账单类型只能是 INCOME 或 EXPENSE")
        String type,

        @NotNull(message = "请选择分类")
        Long categoryId,

        @NotNull(message = "请输入金额")
        @DecimalMin(value = "0.01", message = "金额必须大于 0")
        @DecimalMax(value = "99999999.99", message = "金额超出上限")
        BigDecimal amount,

        @Size(max = 255, message = "备注最长 255 个字符")
        String remark,

        LocalDate billDate,

        String payMethod,

        @Size(max = 200, message = "标签最长 200 个字符")
        String tags
) {
}
