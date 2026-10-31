package com.jizhangbao.module.ai.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 自然语言记账解析结果（结构化，供前端确认后落账）。
 */
public record BillParseVO(
        String type,
        String typeLabel,
        BigDecimal amount,
        Long categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColor,
        Long parentCategoryId,
        String parentCategoryName,
        LocalDate billDate,
        String dateLabel,
        String remark,
        String payMethod,
        /** 0~1 之间的置信度，规则引擎按命中字段数给出，大模型固定 0.9 */
        double confidence,
        /** LLM / RULE */
        String engine,
        /** 是否所有关键字段都识别成功（金额 + 分类） */
        boolean complete,
        String rawText,
        /** 需要用户补充确认的提示 */
        List<String> hints
) {
}
