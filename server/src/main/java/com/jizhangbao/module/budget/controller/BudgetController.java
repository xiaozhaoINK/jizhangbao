package com.jizhangbao.module.budget.controller;

import com.jizhangbao.common.api.Result;
import com.jizhangbao.module.budget.dto.BudgetRequest;
import com.jizhangbao.module.budget.dto.BudgetStatusVO;
import com.jizhangbao.module.budget.dto.BudgetVO;
import com.jizhangbao.module.budget.service.BudgetService;
import com.jizhangbao.security.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 预算与超支提醒接口。
 */
@Tag(name = "预算管理")
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Operation(summary = "某月预算列表")
    @GetMapping
    public Result<List<BudgetVO>> list(@RequestParam(required = false) String yearMonth) {
        return Result.ok(budgetService.list(AuthContext.userId(), yearMonth));
    }

    @Operation(summary = "预算执行状态与提醒", description = "含总预算进度、分类预算进度与超支提醒")
    @GetMapping("/status")
    public Result<BudgetStatusVO> status(@RequestParam(required = false) String yearMonth) {
        return Result.ok(budgetService.status(AuthContext.userId(), yearMonth));
    }

    @Operation(summary = "设置预算")
    @PostMapping
    public Result<BudgetVO> create(@Valid @RequestBody BudgetRequest request) {
        return Result.ok("预算设置成功", budgetService.create(AuthContext.userId(), request));
    }

    @Operation(summary = "修改预算")
    @PutMapping("/{id}")
    public Result<BudgetVO> update(@PathVariable Long id, @Valid @RequestBody BudgetRequest request) {
        return Result.ok("修改成功", budgetService.update(AuthContext.userId(), id, request));
    }

    @Operation(summary = "删除预算")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        budgetService.delete(AuthContext.userId(), id);
        return Result.ok("已删除", null);
    }

    @Operation(summary = "复用上月预算")
    @PostMapping("/copy-last-month")
    public Result<Map<String, Object>> copyLastMonth(@RequestParam(required = false) String yearMonth) {
        int copied = budgetService.copyFromLastMonth(AuthContext.userId(), yearMonth);
        return Result.ok("已复制 " + copied + " 条预算", Map.of("copied", copied));
    }
}
