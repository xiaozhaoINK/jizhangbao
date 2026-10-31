package com.jizhangbao.module.budget.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhangbao.module.budget.entity.Budget;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预算 Mapper。
 */
@Mapper
public interface BudgetMapper extends BaseMapper<Budget> {
}
