package com.jizhangbao.module.ai.engine;

import com.jizhangbao.module.category.entity.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * AI 引擎抽象：让「真实大模型」与「本地规则引擎」共享同一套调用契约。
 */
public interface AiEngine {

    /** 引擎标识：LLM / RULE */
    String name();

    /** 是否为大模型引擎 */
    boolean llm();

    /** 自然语言账单解析 */
    ParsedBill parseBill(ParseInput input);

    /**
     * 财务问答。
     *
     * @param onChunk 逐块回调，用于 SSE 流式输出
     * @return 完整回答
     */
    String chat(String systemPrompt, List<ChatTurn> history, String question, java.util.function.Consumer<String> onChunk);

    /** 一次性补全（月度报告等场景） */
    String complete(String systemPrompt, String userPrompt);

    /* ==================== 数据结构 ==================== */

    record ParseInput(String text, List<Category> expenseCategories, List<Category> incomeCategories) {
    }

    record ParsedBill(String type,
                      BigDecimal amount,
                      String categoryName,
                      LocalDate billDate,
                      String remark,
                      String payMethod,
                      double confidence) {
    }

    record ChatTurn(String role, String content) {
    }
}
