package com.jizhangbao.module.category.controller;

import com.jizhangbao.common.api.Result;
import com.jizhangbao.module.category.dto.CategoryRequest;
import com.jizhangbao.module.category.dto.CategoryVO;
import com.jizhangbao.module.category.service.CategoryService;
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

/**
 * 分类管理接口。
 */
@Tag(name = "分类管理")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "分类树", description = "type 可选 INCOME / EXPENSE，不传返回全部")
    @GetMapping
    public Result<List<CategoryVO>> tree(@RequestParam(required = false) String type) {
        return Result.ok(categoryService.tree(AuthContext.userId(), type));
    }

    @Operation(summary = "分类平铺列表")
    @GetMapping("/flat")
    public Result<List<CategoryVO>> flat(@RequestParam(required = false) String type) {
        return Result.ok(categoryService.list(AuthContext.userId(), type));
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public Result<CategoryVO> create(@Valid @RequestBody CategoryRequest request) {
        return Result.ok(categoryService.create(AuthContext.userId(), request));
    }

    @Operation(summary = "编辑分类")
    @PutMapping("/{id}")
    public Result<CategoryVO> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return Result.ok(categoryService.update(AuthContext.userId(), id, request));
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(AuthContext.userId(), id);
        return Result.ok();
    }
}
