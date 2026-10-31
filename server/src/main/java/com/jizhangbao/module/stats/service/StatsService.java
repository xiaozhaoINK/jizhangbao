package com.jizhangbao.module.stats.service;

import com.jizhangbao.module.stats.dto.CategoryStatVO;
import com.jizhangbao.module.stats.dto.DayStatVO;
import com.jizhangbao.module.stats.dto.MonthCompareVO;
import com.jizhangbao.module.stats.dto.OverviewVO;
import com.jizhangbao.module.stats.dto.TrendPointVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计分析服务。
 */
public interface StatsService {

    /** 首页概览：本月 / 今日 / 环比 / 预算 / 连续记账 */
    OverviewVO overview(Long userId, String yearMonth);

    /** 收支趋势（按天或按月） */
    List<TrendPointVO> trend(Long userId, LocalDate start, LocalDate end, String granularity);

    /** 分类占比（level = parent 按大类 / child 按二级分类） */
    List<CategoryStatVO> categoryStats(Long userId, LocalDate start, LocalDate end, String type, String level);

    /** 一年 12 个月的收支对比 */
    List<MonthCompareVO> monthlyCompare(Long userId, int year);

    /** 某月每天的收支（日历视图） */
    List<DayStatVO> calendar(Long userId, String yearMonth);

    /** 近 N 个月的收支对比（移动端常用） */
    List<MonthCompareVO> recentMonths(Long userId, int months);
}
