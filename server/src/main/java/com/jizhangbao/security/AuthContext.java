package com.jizhangbao.security;

import com.jizhangbao.common.exception.BizException;
import com.jizhangbao.common.exception.ErrorCode;

/**
 * 请求级登录上下文（ThreadLocal 存储，拦截器负责清理）。
 */
public final class AuthContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long userId() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user.id();
    }

    public static Long userIdOrNull() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.id();
    }

    public static String username() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.username();
    }

    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && user.isAdmin();
    }

    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    public static void clear() {
        HOLDER.remove();
    }
}
