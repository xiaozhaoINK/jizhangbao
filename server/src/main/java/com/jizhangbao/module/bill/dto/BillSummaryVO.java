package com.jizhangbao.module.bill.dto;

import java.math.BigDecimal;

/**
 * 区间收支合计。
 */
public record BillSummaryVO(
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance,
        Long count
) {
}
