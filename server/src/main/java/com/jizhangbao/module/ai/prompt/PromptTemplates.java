package com.jizhangbao.module.ai.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词模板加载与渲染。
 *
 * <p>模板放在 {@code resources/prompts/*.st}，占位符使用 <code>{{name}}</code>，
 * 把「提示词」当成配置而不是硬编码字符串，方便迭代与评审。</p>
 */
@Component
public class PromptTemplates {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplates.class);

    public static final String BILL_PARSE = "ai-bill-parse";
    public static final String CHAT = "ai-chat";
    public static final String REPORT = "ai-report";
    public static final String ANALYSIS = "ai-analysis";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String render(String templateName, Map<String, String> variables) {
        String template = load(templateName);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}",
                    entry.getValue() == null ? "" : entry.getValue());
        }
        return template;
    }

    private String load(String templateName) {
        return cache.computeIfAbsent(templateName, name -> {
            ClassPathResource resource = new ClassPathResource("prompts/" + name + ".st");
            try {
                return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("提示词模板加载失败: {}", name, e);
                return "";
            }
        });
    }
}
