package com.jizhangbao.module.category.service;

import com.jizhangbao.module.category.dto.CategoryRequest;
import com.jizhangbao.module.category.dto.CategoryVO;
import com.jizhangbao.module.category.entity.Category;

import java.util.List;
import java.util.Map;

/**
 * 分类服务。
 */
public interface CategoryService {

    /** 分类树（一级 + children），type 为 null 时返回全部 */
    List<CategoryVO> tree(Long userId, String type);

    /** 平铺列表 */
    List<CategoryVO> list(Long userId, String type);

    List<Category> listEntity(Long userId);

    /** 取属于该用户的分类，不存在或不属于则抛异常 */
    Category getOwned(Long userId, Long categoryId);

    /** id -> 分类名称 */
    Map<Long, String> nameMap(Long userId);

    CategoryVO create(Long userId, CategoryRequest request);

    CategoryVO update(Long userId, Long id, CategoryRequest request);

    void delete(Long userId, Long id);

    /** 新用户注册时初始化内置分类 */
    void initDefaultCategories(Long userId);

    /**
     * AI 记账用：根据自然语言中的关键词推断分类。
     *
     * @return 命中的二级分类（优先）或一级分类，未命中返回 null
     */
    Category matchByKeyword(Long userId, String type, String text);
}
