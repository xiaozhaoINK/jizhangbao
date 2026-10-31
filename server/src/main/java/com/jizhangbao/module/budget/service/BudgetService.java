package com.jizhangbao.module.budget.service;

import com.jizhangbao.module.budget.dto.BudgetRequest;
import com.jizhangbao.module.budget.dto.BudgetStatusVO;
import com.jizhangbao.module.budget.dto.BudgetVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 预算服务。
 */
public interface BudgetService {

    /** 某月预算列表（含执行情况） */
    List<BudgetVO> list(Long userId, String yearMonth);

    /** 总预算状态 + 分类预算 + 超支提醒 */
    BudgetStatusVO status(Long userId, String yearMonth);

    BudgetVO create(Long userId, BudgetRequest request);

    BudgetVO update(Long userId, Long id, BudgetRequest request);

    void delete(Long userId, Long id);

    /** 当月总预算金额，未设置返回 0 */
    BigDecimal totalBudgetOf(Long userId, String yearMonth);

    /** 当月支出合计，用于预算进度 */
    BigDecimal totalUsedOf(Long userId, String yearMonth);

    /** 复制上月预算到指定月份（便捷操作） */
    int copyFromLastMonth(Long userId, String yearMonth);
}
