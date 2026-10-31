package com.jizhangbao.module.ai.dto;

import java.time.LocalDateTime;

/**
 * AI 会话列表项。
 */
public record ConversationVO(
        Long id,
        String title,
        Integer messageCount,
        LocalDateTime updatedAt
) {
}
