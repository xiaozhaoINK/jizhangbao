package com.jizhangbao.module.report.controller;

import com.jizhangbao.common.api.Result;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.module.report.dto.MonthlyReportVO;
import com.jizhangbao.module.report.service.MonthlyReportService;
import com.jizhangbao.security.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 月度消费分析报告接口。
 */
@Tag(name = "消费洞察报告")
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final MonthlyReportService reportService;

    public ReportController(MonthlyReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "获取指定月份报告", description = "不存在时自动生成")
    @GetMapping("/monthly/{yearMonth}")
    public Result<MonthlyReportVO> monthly(@PathVariable String yearMonth) {
        return Result.ok(reportService.getOrGenerate(AuthContext.userId(), yearMonth));
    }

    @Operation(summary = "获取当月报告", description = "不传月份时默认当月，不存在自动生成")
    @GetMapping("/current")
    public Result<MonthlyReportVO> current(@RequestParam(required = false) String yearMonth) {
        return Result.ok(reportService.getOrGenerate(AuthContext.userId(), yearMonth));
    }

    @Operation(summary = "重新生成指定月份报告")
    @PostMapping("/monthly/{yearMonth}/generate")
    public Result<MonthlyReportVO> generate(@PathVariable String yearMonth) {
        return Result.ok("报告已生成", reportService.generate(AuthContext.userId(), yearMonth));
    }

    @Operation(summary = "重新生成当月报告")
    @PostMapping("/generate")
    public Result<MonthlyReportVO> generateCurrent(@RequestParam(required = false) String yearMonth) {
        String month = (yearMonth == null || yearMonth.isBlank()) ? DateUtils.currentMonth() : yearMonth;
        return Result.ok("报告已生成", reportService.generate(AuthContext.userId(), month));
    }

    @Operation(summary = "历史报告列表")
    @GetMapping("/monthly")
    public Result<List<MonthlyReportVO>> list(@RequestParam(defaultValue = "12") int limit) {
        return Result.ok(reportService.list(AuthContext.userId(), limit));
    }
}
