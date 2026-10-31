package com.jizhangbao.security;

import com.jizhangbao.common.enums.UserRole;

/**
 * 当前登录用户信息（由 JWT 解析得到，随请求线程传递）。
 */
public record LoginUser(Long id, String username, String role) {

    public boolean isAdmin() {
        return UserRole.ADMIN.name().equalsIgnoreCase(role);
    }
}
