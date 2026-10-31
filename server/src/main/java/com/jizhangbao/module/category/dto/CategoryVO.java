package com.jizhangbao.module.category.dto;

import java.util.List;

/**
 * 分类视图（一级分类带 children）。
 */
public record CategoryVO(
        Long id,
        Long parentId,
        String name,
        String type,
        String icon,
        String color,
        Integer sort,
        Boolean system,
        Long billCount,
        List<CategoryVO> children
) {
    public CategoryVO withChildren(List<CategoryVO> children) {
        return new CategoryVO(id, parentId, name, type, icon, color, sort, system, billCount, children);
    }
}
