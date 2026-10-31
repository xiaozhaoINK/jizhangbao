package com.jizhangbao.module.budget.entity;

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
 * 月度预算（categoryId = 0 表示当月总预算）。
 */
@Data
@TableName("t_budget")
public class Budget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 预算月份 yyyy-MM。
     *
     * <p>数据库列名用 {@code bill_month}，因为 {@code YEAR_MONTH} 在 MySQL 中是保留字。</p>
     */
    @TableField("bill_month")
    private String yearMonth;

    private Long categoryId;

    private BigDecimal amount;

    /** 达到该百分比时提醒，默认 80 */
    private Integer alertThreshold;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
