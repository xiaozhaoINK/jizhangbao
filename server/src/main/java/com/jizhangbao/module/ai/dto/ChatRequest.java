package com.jizhangbao.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 财务问答请求。
 */
public record ChatRequest(
        Long conversationId,

        @NotBlank(message = "请输入你的问题")
        @Size(max = 2000, message = "问题最长 2000 个字符")
        String message
) {
}
