package com.jizhangbao.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 */
public record RegisterRequest(
        @NotBlank(message = "请输入账号")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,19}$", message = "账号需以字母开头，3-20 位字母数字下划线")
        String username,

        @NotBlank(message = "请输入密码")
        @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
        String password,

        @Size(max = 20, message = "昵称最长 20 个字符")
        String nickname
) {
}
