package com.jizhangbao.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改密码请求。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "请输入原密码")
        String oldPassword,

        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 32, message = "新密码长度需在 6-32 位之间")
        String newPassword
) {
}
