package com.jizhangbao.module.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 调用日志（不含逻辑删除，用于后台统计与成本观察）。
 */
@Data
@TableName("t_ai_usage_log")
public class AiUsageLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** PARSE / CHAT / ANALYSIS / REPORT */
    private String scene;

    /** LLM / RULE */
    private String engine;

    private Integer inputChars;

    private Integer success;

    private Integer costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
