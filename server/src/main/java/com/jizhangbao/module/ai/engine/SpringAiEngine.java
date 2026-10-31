package com.jizhangbao.module.ai.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.config.AppProperties;
import com.jizhangbao.module.ai.prompt.PromptTemplates;
import com.jizhangbao.module.category.entity.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 大模型引擎：通过 Spring AI 的 {@link ChatClient} 调用 OpenAI 兼容协议的大模型
 * （DeepSeek / 通义千问 / OpenAI / 本地 vLLM 均可）。
 *
 * <p>启用方式：{@code --spring.profiles.active=llm} 并提供 {@code OPENAI_API_KEY}。</p>
 */
@Component
@ConditionalOnProperty(name = "jizhangbao.ai.mode", havingValue = "llm")
public class SpringAiEngine implements AiEngine {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEngine.class);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChatClient chatClient;
    private final PromptTemplates prompts;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public SpringAiEngine(ChatClient.Builder chatClientBuilder,
                          PromptTemplates prompts,
                          ObjectMapper objectMapper,
                          AppProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.prompts = prompts;
        this.objectMapper = objectMapper;
        this.properties = properties;
        log.info("AI 引擎已启用大模型模式：model={}, provider={}",
                properties.getAi().getModel(), properties.getAi().getProvider());
    }

    @Override
    public String name() {
        return "LLM";
    }

    @Override
    public boolean llm() {
        return true;
    }

    @Override
    public ParsedBill parseBill(ParseInput input) {
        LocalDate today = LocalDate.now();
        Map<String, String> variables = new HashMap<>();
        variables.put("expenseCategories", joinNames(input.expenseCategories()));
        variables.put("incomeCategories", joinNames(input.incomeCategories()));
        variables.put("today", today.toString());
        variables.put("yesterday", today.minusDays(1).toString());
        variables.put("weekday", weekdayCn(today));
        String systemPrompt = prompts.render(PromptTemplates.BILL_PARSE, variables);

        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(input.text())
                .call()
                .content();
        log.debug("大模型解析结果: {}", content);

        JsonNode node = readJson(content);
        if (node == null) {
            throw new IllegalStateException("大模型返回内容无法解析为 JSON: " + content);
        }
        String type = node.path("type").asText("EXPENSE").toUpperCase();
        BigDecimal amount = node.path("amount").isNumber()
                ? node.path("amount").decimalValue()
                : parseAmount(node.path("amount").asText(null));
        String category = node.path("category").asText(null);
        LocalDate date = parseDate(node.path("date").asText(null), today);
        String remark = node.path("remark").asText(null);
        String payMethod = node.path("payMethod").isNull() ? null : node.path("payMethod").asText(null);

        return new ParsedBill(type, amount, category, date, remark, payMethod,
                amount == null ? 0.5 : 0.9);
    }

    @Override
    public String chat(String systemPrompt, List<ChatTurn> history, String question, Consumer<String> onChunk) {
        StringBuilder answer = new StringBuilder();
        StringBuilder prompt = new StringBuilder(systemPrompt);
        try {
            chatClient.prompt()
                    .system(prompt.toString())
                    .user(question)
                    .stream()
                    .content()
                    .doOnNext(chunk -> {
                        answer.append(chunk);
                        onChunk.accept(chunk);
                    })
                    .blockLast(Duration.ofSeconds(Math.max(10, properties.getAi().getTimeoutSeconds())));
        } catch (Exception e) {
            log.warn("大模型流式调用失败，返回降级提示: {}", e.getMessage());
            String fallback = "抱歉，AI 服务暂时不可用（" + e.getClass().getSimpleName() + "），请稍后再试。";
            answer.append(fallback);
            onChunk.accept(fallback);
        }
        return answer.toString();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("大模型补全失败: {}", e.getMessage());
            throw new IllegalStateException("AI 服务暂时不可用", e);
        }
    }

    public String modelName() {
        return properties.getAi().getModel();
    }

    public String describe() {
        return "Spring AI · " + properties.getAi().getProvider() + " · " + properties.getAi().getModel();
    }

    /* ==================== 辅助 ==================== */

    private String joinNames(List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return "（暂无）";
        }
        Map<String, List<Category>> byParent = categories.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(c -> parentName(categories, c), java.util.LinkedHashMap::new,
                        Collectors.toList()));
        StringBuilder sb = new StringBuilder();
        byParent.forEach((parent, children) -> {
            sb.append("- ").append(parent).append("：");
            sb.append(children.stream().map(Category::getName).collect(Collectors.joining("、")));
            sb.append('\n');
        });
        return sb.toString();
    }

    private String parentName(List<Category> categories, Category child) {
        return categories.stream()
                .filter(c -> c.getId().equals(child.getParentId()))
                .map(Category::getName)
                .findFirst()
                .orElse("其它");
    }

    private JsonNode readJson(String content) {
        if (content == null) {
            return null;
        }
        String cleaned = content.replace("```json", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(cleaned.substring(start, end + 1));
        } catch (Exception e) {
            log.warn("JSON 解析失败: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal parseAmount(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String text, LocalDate fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(text.trim(), DateUtils.YMD);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String weekdayCn(LocalDate date) {
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        return names[date.getDayOfWeek().getValue() - 1];
    }
}
