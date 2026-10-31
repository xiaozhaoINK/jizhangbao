package com.jizhangbao.module.common.controller;

import com.jizhangbao.common.api.Result;
import com.jizhangbao.common.enums.BillType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 公共元数据接口：字典、枚举、健康检查。
 *
 * <p>前端把「支付方式 / 账单类型」等选项统一从后端取，避免两端各维护一份写死的数据。</p>
 */
@Tag(name = "公共元数据")
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    public static final List<String> PAY_METHODS = List.of(
            "微信", "支付宝", "银行卡", "信用卡", "花呗", "现金", "云闪付", "其他");

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "status", "UP",
                "service", "jizhangbao-server",
                "version", "1.0.0"
        ));
    }

    @Operation(summary = "前端字典")
    @GetMapping("/dicts")
    public Result<Map<String, Object>> dicts() {
        return Result.ok(Map.of(
                "billTypes", List.of(
                        Map.of("value", BillType.EXPENSE.name(), "label", "支出"),
                        Map.of("value", BillType.INCOME.name(), "label", "收入")),
                "payMethods", PAY_METHODS,
                "billSources", List.of(
                        Map.of("value", "MANUAL", "label", "手动记账"),
                        Map.of("value", "AI", "label", "AI 记账"))
        ));
    }
}
