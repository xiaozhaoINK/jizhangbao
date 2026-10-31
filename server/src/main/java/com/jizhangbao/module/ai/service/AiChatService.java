package com.jizhangbao.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jizhangbao.common.exception.BizException;
import com.jizhangbao.common.exception.ErrorCode;
import com.jizhangbao.config.AppProperties;
import com.jizhangbao.module.ai.dto.ChatMessageVO;
import com.jizhangbao.module.ai.dto.ConversationVO;
import com.jizhangbao.module.ai.engine.AiEngine;
import com.jizhangbao.module.ai.entity.AiConversation;
import com.jizhangbao.module.ai.entity.AiMessage;
import com.jizhangbao.module.ai.entity.AiUsageLog;
import com.jizhangbao.module.ai.mapper.AiConversationMapper;
import com.jizhangbao.module.ai.mapper.AiMessageMapper;
import com.jizhangbao.module.ai.mapper.AiUsageLogMapper;
import com.jizhangbao.module.ai.prompt.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 财务问答：SSE 流式输出 + 会话持久化。
 *
 * <p>让模型「言之有数」的关键在于 {@link FinanceContextBuilder}：每一次提问都会把用户
 * 当月真实账本聚合数据拼进提示词，模型只负责表达，不负责计算。</p>
 */
@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final AiEngine aiEngine;
    private final FinanceContextBuilder contextBuilder;
    private final LocalAnswerGenerator localAnswerGenerator;
    private final PromptTemplates promptTemplates;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiUsageLogMapper usageLogMapper;
    private final AppProperties properties;
    private final ThreadPoolTaskExecutor executor;

    public AiChatService(AiEngine aiEngine,
                         FinanceContextBuilder contextBuilder,
                         LocalAnswerGenerator localAnswerGenerator,
                         PromptTemplates promptTemplates,
                         AiConversationMapper conversationMapper,
                         AiMessageMapper messageMapper,
                         AiUsageLogMapper usageLogMapper,
                         AppProperties properties,
                         @Qualifier("aiExecutor") ThreadPoolTaskExecutor executor) {
        this.aiEngine = aiEngine;
        this.contextBuilder = contextBuilder;
        this.localAnswerGenerator = localAnswerGenerator;
        this.promptTemplates = promptTemplates;
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.usageLogMapper = usageLogMapper;
        this.properties = properties;
        this.executor = executor;
    }

    /**
     * 流式问答：立即返回 SseEmitter，后台线程逐块推送。
     */
    public SseEmitter stream(Long userId, Long conversationId, String question) {
        AiConversation conversation = ensureConversation(userId, conversationId, question);
        saveMessage(userId, conversation.getId(), "user", question);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicLong assistantMessageId = new AtomicLong(0L);

        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> log.debug("SSE 连接异常: {}", e.getMessage()));

        executor.execute(() -> {
            long start = System.currentTimeMillis();
            StringBuilder answerBuffer = new StringBuilder();
            try {
                sendEvent(emitter, "meta", Map.of(
                        "conversationId", conversation.getId(),
                        "engine", aiEngine.name(),
                        "model", aiEngine.llm() ? properties.getAi().getModel() : "local-rule-engine"
                ));

                FinanceSnapshot snapshot = contextBuilder.build(userId, null);
                String reply;
                if (aiEngine.llm()) {
                    Map<String, String> variables = new HashMap<>();
                    variables.put("financeContext", snapshot.text());
                    variables.put("history", renderHistory(userId, conversation.getId()));
                    variables.put("question", question);
                    String systemPrompt = promptTemplates.render(PromptTemplates.CHAT, variables);
                    List<AiEngine.ChatTurn> history = history(userId, conversation.getId());
                    reply = aiEngine.chat(systemPrompt, history, question, chunk -> {
                        answerBuffer.append(chunk);
                        sendEvent(emitter, "delta", Map.of("content", chunk));
                    });
                } else {
                    reply = localAnswerGenerator.answer(snapshot, question);
                    // 规则模式下也做流式输出，前端体验与真流式一致
                    for (String piece : split(reply)) {
                        answerBuffer.append(piece);
                        sendEvent(emitter, "delta", Map.of("content", piece));
                        sleep(18);
                    }
                }

                AiMessage saved = saveMessage(userId, conversation.getId(), "assistant", reply);
                assistantMessageId.set(saved == null ? 0L : saved.getId());
                sendEvent(emitter, "done", Map.of(
                        "conversationId", conversation.getId(),
                        "messageId", assistantMessageId.get(),
                        "engine", aiEngine.name()
                ));
                emitter.complete();
                logUsage(userId, "CHAT", question.length(), true, System.currentTimeMillis() - start);
            } catch (Exception e) {
                log.warn("AI 问答失败: {}", e.getMessage());
                sendEvent(emitter, "error", Map.of("message",
                        "AI 服务暂时不可用，请稍后再试"));
                if (answerBuffer.length() == 0) {
                    saveMessage(userId, conversation.getId(), "assistant",
                            "抱歉，我这边出了点问题，请稍后再问我一次。");
                }
                emitter.complete();
                logUsage(userId, "CHAT", question.length(), false, System.currentTimeMillis() - start);
            }
        });
        return emitter;
    }

    public List<ConversationVO> conversations(Long userId) {
        List<AiConversation> list = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getUpdatedAt)
                .last("LIMIT 30"));
        List<ConversationVO> result = new ArrayList<>();
        for (AiConversation conversation : list) {
            Long count = messageMapper.selectCount(new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getConversationId, conversation.getId()));
            result.add(new ConversationVO(conversation.getId(), conversation.getTitle(),
                    count == null ? 0 : count.intValue(), conversation.getUpdatedAt()));
        }
        return result;
    }

    public List<ChatMessageVO> messages(Long userId, Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByAsc(AiMessage::getId))
                .stream()
                .map(m -> new ChatMessageVO(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    public void deleteConversation(Long userId, Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        conversationMapper.deleteById(conversationId);
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId));
    }

    /* ==================== 内部方法 ==================== */

    private AiConversation ensureConversation(Long userId, Long conversationId, String question) {
        if (conversationId != null && conversationId > 0) {
            AiConversation existing = conversationMapper.selectById(conversationId);
            if (existing != null && existing.getUserId().equals(userId)) {
                return existing;
            }
        }
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(question.length() > 20 ? question.substring(0, 20) + "…" : question);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private AiMessage saveMessage(Long userId, Long conversationId, String role, String content) {
        try {
            AiMessage message = new AiMessage();
            message.setConversationId(conversationId);
            message.setUserId(userId);
            message.setRole(role);
            message.setContent(content);
            messageMapper.insert(message);
            return message;
        } catch (Exception e) {
            log.warn("消息保存失败: {}", e.getMessage());
            return null;
        }
    }

    private List<AiEngine.ChatTurn> history(Long userId, Long conversationId) {
        int limit = Math.max(2, properties.getAi().getMaxHistoryMessages());
        List<AiMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT " + limit));
        List<AiEngine.ChatTurn> turns = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiMessage message = messages.get(i);
            turns.add(new AiEngine.ChatTurn(message.getRole(), message.getContent()));
        }
        return turns;
    }

    private String renderHistory(Long userId, Long conversationId) {
        List<AiEngine.ChatTurn> turns = history(userId, conversationId);
        if (turns.isEmpty()) {
            return "（无）";
        }
        StringBuilder sb = new StringBuilder();
        for (AiEngine.ChatTurn turn : turns) {
            sb.append("user".equals(turn.role()) ? "用户：" : "小账：")
                    .append(turn.content()).append('\n');
        }
        return sb.toString();
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            // 客户端已断开，忽略
        }
    }

    /** 把整段回答切成小块，模拟流式输出 */
    private List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return pieces;
        }
        int index = 0;
        int step = 6;
        while (index < text.length()) {
            int end = Math.min(text.length(), index + step);
            pieces.add(text.substring(index, end));
            index = end;
        }
        return pieces;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logUsage(Long userId, String scene, int chars, boolean success, long costMs) {
        try {
            AiUsageLog entity = new AiUsageLog();
            entity.setUserId(userId);
            entity.setScene(scene);
            entity.setEngine(aiEngine.name());
            entity.setInputChars(chars);
            entity.setSuccess(success ? 1 : 0);
            entity.setCostMs((int) Math.min(costMs, Integer.MAX_VALUE));
            usageLogMapper.insert(entity);
        } catch (Exception e) {
            log.debug("AI 日志写入失败: {}", e.getMessage());
        }
    }
}
