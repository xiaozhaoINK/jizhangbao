package com.jizhangbao.module.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改资料请求。
 */
public record UpdateProfileRequest(
        @Size(max = 20, message = "昵称最长 20 个字符")
        String nickname,

        @Email(message = "邮箱格式不正确")
        String email,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        String avatar
) {
}
