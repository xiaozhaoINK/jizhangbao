package com.jizhangbao.common.enums;

/**
 * 账单类型。
 */
public enum BillType {

    /** 收入 */
    INCOME,
    /** 支出 */
    EXPENSE;

    public static BillType of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (BillType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }

    public String label() {
        return this == INCOME ? "收入" : "支出";
    }
}
