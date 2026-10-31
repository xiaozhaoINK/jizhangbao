package com.jizhangbao.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 自然语言记账请求，例如「昨天午饭花了 30」。
 */
public record BillParseRequest(
        @NotBlank(message = "请输入要记账的内容")
        @Size(max = 200, message = "内容最长 200 个字符")
        String text
) {
}
