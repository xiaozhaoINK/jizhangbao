package com.jizhangbao.module.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 预算设置请求。
 */
public record BudgetRequest(
        @NotNull(message = "请选择预算月份")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "月份格式需为 yyyy-MM")
        String yearMonth,

        /** 0 或空表示总预算 */
        Long categoryId,

        @NotNull(message = "请输入预算金额")
        @DecimalMin(value = "1", message = "预算金额需大于 1 元")
        BigDecimal amount,

        @Min(value = 10, message = "提醒阈值最低 10%")
        @Max(value = 100, message = "提醒阈值最高 100%")
        Integer alertThreshold
) {
}
