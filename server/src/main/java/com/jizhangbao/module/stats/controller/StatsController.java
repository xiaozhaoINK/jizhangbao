package com.jizhangbao.module.stats.controller;

import com.jizhangbao.common.api.Result;
import com.jizhangbao.module.stats.dto.CategoryStatVO;
import com.jizhangbao.module.stats.dto.DayStatVO;
import com.jizhangbao.module.stats.dto.MonthCompareVO;
import com.jizhangbao.module.stats.dto.OverviewVO;
import com.jizhangbao.module.stats.dto.TrendPointVO;
import com.jizhangbao.module.stats.service.StatsService;
import com.jizhangbao.security.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 图表统计接口：概览、趋势、分类占比、月度对比、日历。
 */
@Tag(name = "统计分析")
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "收支概览", description = "本月/今日收支、环比、日均、预算使用、连续记账天数")
    @GetMapping("/overview")
    public Result<OverviewVO> overview(@RequestParam(required = false) String yearMonth) {
        return Result.ok(statsService.overview(AuthContext.userId(), yearMonth));
    }

    @Operation(summary = "收支趋势", description = "granularity=day 按天，month 按月；跨度超过 120 天自动按月聚合")
    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "day") String granularity) {
        return Result.ok(statsService.trend(AuthContext.userId(), startDate, endDate, granularity));
    }

    @Operation(summary = "分类占比", description = "type=EXPENSE|INCOME；level=parent|child")
    @GetMapping("/category")
    public Result<List<CategoryStatVO>> category(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "EXPENSE") String type,
            @RequestParam(defaultValue = "parent") String level) {
        return Result.ok(statsService.categoryStats(AuthContext.userId(), startDate, endDate, type, level));
    }

    @Operation(summary = "年度 12 个月对比")
    @GetMapping("/monthly")
    public Result<List<MonthCompareVO>> monthly(@RequestParam(required = false) Integer year) {
        int target = year == null ? LocalDate.now().getYear() : year;
        return Result.ok(statsService.monthlyCompare(AuthContext.userId(), target));
    }

    @Operation(summary = "近 N 个月对比")
    @GetMapping("/recent-months")
    public Result<List<MonthCompareVO>> recentMonths(@RequestParam(defaultValue = "6") int months) {
        return Result.ok(statsService.recentMonths(AuthContext.userId(), months));
    }

    @Operation(summary = "月历收支", description = "用于日历视图与支出热力图")
    @GetMapping("/calendar")
    public Result<List<DayStatVO>> calendar(@RequestParam(required = false) String yearMonth) {
        return Result.ok(statsService.calendar(AuthContext.userId(), yearMonth));
    }
}
