package com.jizhangbao.module.user.dto;

import java.time.LocalDateTime;

/**
 * 用户信息视图（绝不包含密码字段）。
 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        String avatar,
        String email,
        String phone,
        String role,
        Integer status,
        LocalDateTime lastLogin,
        LocalDateTime createdAt
) {
}
