package com.jizhangbao.module.user.dto;

/**
 * 登录 / 注册成功后的凭证。
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        UserVO user
) {
    public static LoginResponse of(String token, long expiresIn, UserVO user) {
        return new LoginResponse(token, "Bearer", expiresIn, user);
    }
}
