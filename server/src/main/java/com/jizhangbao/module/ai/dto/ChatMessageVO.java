package com.jizhangbao.module.ai.dto;

import java.time.LocalDateTime;

/**
 * AI 会话消息视图。
 */
public record ChatMessageVO(
        Long id,
        String role,
        String content,
        LocalDateTime createdAt
) {
}
