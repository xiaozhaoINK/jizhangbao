package com.jizhangbao.module.user.controller;

import com.jizhangbao.common.api.Result;
import com.jizhangbao.config.AppProperties;
import com.jizhangbao.module.user.dto.LoginRequest;
import com.jizhangbao.module.user.dto.LoginResponse;
import com.jizhangbao.module.user.dto.RegisterRequest;
import com.jizhangbao.module.user.dto.UserVO;
import com.jizhangbao.module.user.service.UserService;
import com.jizhangbao.security.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 登出 / 当前用户。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AppProperties properties;

    public AuthController(UserService userService, AppProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @Operation(summary = "注册", description = "注册成功后自动登录，并初始化默收支分类")
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok("注册成功", userService.register(request));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok("登录成功", userService.login(request));
    }

    @Operation(summary = "登出", description = "当前 token 会进入 Redis 黑名单直至自然过期")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        userService.logout(resolveToken(request));
        return Result.ok("已退出登录", null);
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.me(AuthContext.userId()));
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(properties.getJwt().getHeader());
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String prefix = properties.getJwt().getPrefix();
        return StringUtils.hasText(prefix) && header.startsWith(prefix)
                ? header.substring(prefix.length()).trim() : header.trim();
    }
}
