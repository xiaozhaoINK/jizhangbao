package com.jizhangbao.common.enums;

/**
 * 用户角色。
 */
public enum UserRole {

    /** 普通用户 */
    USER,
    /** 管理员（可进入后台管理端） */
    ADMIN;

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
