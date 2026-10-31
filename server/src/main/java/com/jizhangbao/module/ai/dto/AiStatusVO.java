package com.jizhangbao.module.ai.dto;

/**
 * AI 能力状态（前端用于展示当前是"大模型模式"还是"本地规则模式"）。
 */
public record AiStatusVO(
        String mode,
        String engine,
        String model,
        String provider,
        boolean llm,
        String description,
        int dailyLimit
) {
}
