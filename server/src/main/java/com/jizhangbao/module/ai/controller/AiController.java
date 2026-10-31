package com.jizhangbao.module.ai.controller;

import com.jizhangbao.common.api.Result;
import com.jizhangbao.config.AppProperties;
import com.jizhangbao.module.ai.dto.AiStatusVO;
import com.jizhangbao.module.ai.dto.AnalysisVO;
import com.jizhangbao.module.ai.dto.BillParseRequest;
import com.jizhangbao.module.ai.dto.BillParseVO;
import com.jizhangbao.module.ai.dto.ChatMessageVO;
import com.jizhangbao.module.ai.dto.ChatRequest;
import com.jizhangbao.module.ai.dto.ConversationVO;
import com.jizhangbao.module.ai.engine.AiEngine;
import com.jizhangbao.module.ai.service.AiAccountingService;
import com.jizhangbao.module.ai.service.AiAnalysisService;
import com.jizhangbao.module.ai.service.AiChatService;
import com.jizhangbao.module.bill.dto.BillVO;
import com.jizhangbao.security.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 能力接口：自然语言记账、财务问答（SSE 流式）、消费洞察。
 */
@Tag(name = "AI 智能记账")
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAccountingService accountingService;
    private final AiChatService chatService;
    private final AiAnalysisService analysisService;
    private final AiEngine aiEngine;
    private final AppProperties properties;

    public AiController(AiAccountingService accountingService, AiChatService chatService,
                        AiAnalysisService analysisService, AiEngine aiEngine, AppProperties properties) {
        this.accountingService = accountingService;
        this.chatService = chatService;
        this.analysisService = analysisService;
        this.aiEngine = aiEngine;
        this.properties = properties;
    }

    @Operation(summary = "AI 能力状态", description = "前端据此展示当前是「大模型模式」还是「本地规则模式」")
    @GetMapping("/status")
    public Result<AiStatusVO> status() {
        boolean llm = aiEngine.llm();
        return Result.ok(new AiStatusVO(
                llm ? "llm" : "rule",
                aiEngine.name(),
                llm ? properties.getAi().getModel() : "local-rule-engine",
                properties.getAi().getProvider(),
                llm,
                llm ? "已接入大模型，语义理解更准确" : "当前为本地规则引擎，离线也能记账与问答",
                20
        ));
    }

    @Operation(summary = "自然语言解析账单", description = "例如「昨天午饭花了 30」，返回结构化结果供用户确认")
    @PostMapping("/bills/parse")
    public Result<BillParseVO> parse(@Valid @RequestBody BillParseRequest request) {
        return Result.ok(accountingService.parse(AuthContext.userId(), request.text()));
    }

    @Operation(summary = "自然语言记账并落库", description = "解析成功直接生成账单")
    @PostMapping("/bills/record")
    public Result<BillVO> record(@Valid @RequestBody BillParseRequest request) {
        return Result.ok("记账成功", accountingService.record(AuthContext.userId(), request.text()));
    }

    @Operation(summary = "AI 财务问答（SSE 流式）", description = "返回 text/event-stream，事件：meta / delta / done / error")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(AuthContext.userId(), request.conversationId(), request.message());
    }

    @Operation(summary = "会话列表")
    @GetMapping("/conversations")
    public Result<List<ConversationVO>> conversations() {
        return Result.ok(chatService.conversations(AuthContext.userId()));
    }

    @Operation(summary = "会话消息明细")
    @GetMapping("/conversations/{id}/messages")
    public Result<List<ChatMessageVO>> messages(@PathVariable Long id) {
        return Result.ok(chatService.messages(AuthContext.userId(), id));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        chatService.deleteConversation(AuthContext.userId(), id);
        return Result.ok();
    }

    @Operation(summary = "消费洞察", description = "按月生成异常提示、变化趋势与省钱建议")
    @PostMapping("/analysis")
    public Result<AnalysisVO> analysis(@RequestParam(required = false) String yearMonth) {
        return Result.ok(analysisService.analyze(AuthContext.userId(), yearMonth));
    }

    @Operation(summary = "获取消费洞察（GET 方式，便于前端直接拉取）")
    @GetMapping("/analysis")
    public Result<AnalysisVO> analysisGet(@RequestParam(required = false) String yearMonth) {
        return Result.ok(analysisService.analyze(AuthContext.userId(), yearMonth));
    }
}
