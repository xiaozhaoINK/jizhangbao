package com.jizhangbao.module.bill.controller;

import com.jizhangbao.common.api.PageResult;
import com.jizhangbao.common.api.Result;
import com.jizhangbao.module.bill.dto.BillQuery;
import com.jizhangbao.module.bill.dto.BillRequest;
import com.jizhangbao.module.bill.dto.BillSummaryVO;
import com.jizhangbao.module.bill.dto.BillVO;
import com.jizhangbao.module.bill.service.BillService;
import com.jizhangbao.security.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 账单接口：增删改查 + 明细筛选 + 区间合计。
 */
@Tag(name = "账单管理")
@RestController
@RequestMapping("/api/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @Operation(summary = "分页查询账单", description = "支持按日/月/年、类型、分类、关键字、金额区间筛选")
    @GetMapping
    public Result<PageResult<BillVO>> page(BillQuery query) {
        return Result.ok(billService.page(AuthContext.userId(), query));
    }

    @Operation(summary = "账单详情")
    @GetMapping("/{id}")
    public Result<BillVO> detail(@PathVariable Long id) {
        return Result.ok(billService.detail(AuthContext.userId(), id));
    }

    @Operation(summary = "记一笔")
    @PostMapping
    public Result<BillVO> create(@Valid @RequestBody BillRequest request) {
        return Result.ok("记账成功", billService.create(AuthContext.userId(), request));
    }

    @Operation(summary = "编辑账单")
    @PutMapping("/{id}")
    public Result<BillVO> update(@PathVariable Long id, @Valid @RequestBody BillRequest request) {
        return Result.ok("修改成功", billService.update(AuthContext.userId(), id, request));
    }

    @Operation(summary = "删除账单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        billService.delete(AuthContext.userId(), id);
        return Result.ok("已删除", null);
    }

    @Operation(summary = "最近账单")
    @GetMapping("/recent")
    public Result<List<BillVO>> recent(@RequestParam(defaultValue = "5") int limit) {
        return Result.ok(billService.recent(AuthContext.userId(), limit));
    }

    @Operation(summary = "区间收支合计")
    @GetMapping("/summary")
    public Result<BillSummaryVO> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().withDayOfMonth(1) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        return Result.ok(billService.summary(AuthContext.userId(), start, end));
    }
}
