package com.jizhangbao.module.report.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度消费分析报告。
 */
@Data
@TableName("t_monthly_report")
public class MonthlyReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 报告月份 yyyy-MM。
     *
     * <p>数据库列名用 {@code bill_month}，因为 {@code YEAR_MONTH} 在 MySQL 中是保留字。</p>
     */
    @TableField("bill_month")
    private String yearMonth;

    private BigDecimal totalIncome;

    private BigDecimal totalExpense;

    private BigDecimal balance;

    private Integer billCount;

    private String topCategory;

    /** Markdown 正文 */
    private String content;

    /** JSON 数组 */
    private String insights;

    /** JSON 数组 */
    private String suggestions;

    /** LLM / RULE */
    private String generator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
