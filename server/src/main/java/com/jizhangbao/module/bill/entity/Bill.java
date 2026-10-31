package com.jizhangbao.module.bill.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 收支账单。
 */
@Data
@TableName("t_bill")
public class Bill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** INCOME / EXPENSE */
    private String type;

    private Long categoryId;

    /** 一级分类 ID（冗余字段，方便按大类聚合统计） */
    private Long parentCategoryId;

    private BigDecimal amount;

    private String remark;

    private LocalDate billDate;

    private LocalDateTime billTime;

    private String payMethod;

    private String tags;

    /** MANUAL / AI */
    private String source;

    /** AI 记账时的原始输入 */
    private String aiInput;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
