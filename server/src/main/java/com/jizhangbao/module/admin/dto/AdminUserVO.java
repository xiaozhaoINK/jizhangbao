package com.jizhangbao.module.admin.dto;

import java.time.LocalDateTime;

/**
 * 后台用户列表项。
 */
public record AdminUserVO(
        Long id,
        String username,
        String nickname,
        String role,
        Integer status,
        long billCount,
        LocalDateTime lastLogin,
        LocalDateTime createdAt
) {
}
