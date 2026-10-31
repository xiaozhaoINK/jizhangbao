package com.jizhangbao.config;

import com.jizhangbao.module.ai.engine.AiEngine;
import com.jizhangbao.module.ai.engine.RuleBasedAiEngine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 引擎装配：默认使用本地规则引擎，{@code jizhangbao.ai.mode=llm} 时使用 Spring AI 大模型引擎。
 */
@Configuration
public class AiEngineConfig {

    @Bean
    @ConditionalOnProperty(name = "jizhangbao.ai.mode", havingValue = "rule", matchIfMissing = true)
    public AiEngine ruleBasedAiEngine() {
        return new RuleBasedAiEngine();
    }
}
