package com.jizhangbao.module.admin.controller;

import com.jizhangbao.common.api.PageResult;
import com.jizhangbao.common.api.Result;
import com.jizhangbao.module.admin.dto.AdminOverviewVO;
import com.jizhangbao.module.admin.dto.AdminUserVO;
import com.jizhangbao.module.admin.service.AdminService;
import com.jizhangbao.security.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 后台管理接口（需要 ADMIN 角色）。
 */
@Tag(name = "后台管理")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "平台数据概览")
    @GetMapping("/overview")
    public Result<AdminOverviewVO> overview() {
        AuthContext.requireAdmin();
        return Result.ok(adminService.overview());
    }

    @Operation(summary = "用户列表")
    @GetMapping("/users")
    public Result<PageResult<AdminUserVO>> users(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size,
                                                 @RequestParam(required = false) String keyword) {
        AuthContext.requireAdmin();
        return Result.ok(adminService.users(page, size, keyword));
    }

    @Operation(summary = "启用 / 禁用用户")
    @PutMapping("/users/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        AuthContext.requireAdmin();
        adminService.updateUserStatus(AuthContext.userId(), id, status);
        return Result.ok("状态已更新", null);
    }

    @Operation(summary = "AI 调用分布")
    @GetMapping("/ai-usage")
    public Result<List<Map<String, Object>>> aiUsage() {
        AuthContext.requireAdmin();
        return Result.ok(adminService.aiUsage());
    }
}
