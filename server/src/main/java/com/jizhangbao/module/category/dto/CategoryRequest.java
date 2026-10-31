package com.jizhangbao.module.category.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 分类新增 / 编辑请求。
 */
public record CategoryRequest(
        @NotBlank(message = "请输入分类名称")
        @Size(max = 10, message = "分类名称最长 10 个字符")
        String name,

        @Pattern(regexp = "INCOME|EXPENSE", message = "分类类型只能是 INCOME 或 EXPENSE")
        String type,

        Long parentId,

        String icon,

        @Pattern(regexp = "^#([0-9a-fA-F]{6})$", message = "颜色格式需为 #RRGGBB")
        String color,

        @Min(value = 0, message = "排序值不能为负")
        @Max(value = 9999, message = "排序值过大")
        Integer sort
) {
}
