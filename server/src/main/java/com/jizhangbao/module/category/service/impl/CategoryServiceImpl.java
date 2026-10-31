package com.jizhangbao.module.category.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jizhangbao.common.enums.BillType;
import com.jizhangbao.common.exception.BizException;
import com.jizhangbao.common.exception.ErrorCode;
import com.jizhangbao.module.bill.entity.Bill;
import com.jizhangbao.module.bill.mapper.BillMapper;
import com.jizhangbao.module.category.CategoryTemplates;
import com.jizhangbao.module.category.dto.CategoryRequest;
import com.jizhangbao.module.category.dto.CategoryVO;
import com.jizhangbao.module.category.entity.Category;
import com.jizhangbao.module.category.mapper.CategoryMapper;
import com.jizhangbao.module.category.service.CategoryService;
import com.jizhangbao.module.category.support.CategoryMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分类服务实现。
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final BillMapper billMapper;

    public CategoryServiceImpl(CategoryMapper categoryMapper, BillMapper billMapper) {
        this.categoryMapper = categoryMapper;
        this.billMapper = billMapper;
    }

    @Override
    public List<CategoryVO> tree(Long userId, String type) {
        List<CategoryVO> flat = list(userId, type);
        Map<Long, List<CategoryVO>> children = flat.stream()
                .filter(c -> c.parentId() != null && c.parentId() > 0)
                .collect(Collectors.groupingBy(CategoryVO::parentId, LinkedHashMap::new, Collectors.toList()));
        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO node : flat) {
            if (node.parentId() == null || node.parentId() == 0) {
                roots.add(node.withChildren(children.getOrDefault(node.id(), List.of())));
            }
        }
        return roots;
    }

    @Override
    public List<CategoryVO> list(Long userId, String type) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .eq(Category::getUserId, userId)
                .eq(StringUtils.hasText(type), Category::getType, type == null ? null : type.toUpperCase())
                .orderByAsc(Category::getParentId)
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId);
        List<Category> categories = categoryMapper.selectList(wrapper);
        Map<Long, Long> counts = billCountByCategory(userId);
        return categories.stream()
                .map(c -> toVO(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Override
    public List<Category> listEntity(Long userId) {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getUserId, userId)
                .orderByAsc(Category::getParentId)
                .orderByAsc(Category::getSort));
    }

    @Override
    public Category getOwned(Long userId, Long categoryId) {
        if (categoryId == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BizException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!category.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return category;
    }

    @Override
    public Map<Long, String> nameMap(Long userId) {
        Map<Long, String> map = new HashMap<>();
        for (Category c : listEntity(userId)) {
            map.put(c.getId(), c.getName());
        }
        return map;
    }

    @Override
    @Transactional
    public CategoryVO create(Long userId, CategoryRequest request) {
        String type = request.type() == null ? BillType.EXPENSE.name() : request.type().toUpperCase();
        Long parentId = request.parentId() == null ? 0L : request.parentId();
        if (parentId > 0) {
            Category parent = getOwned(userId, parentId);
            if (!parent.getType().equals(type)) {
                throw new BizException(ErrorCode.CATEGORY_TYPE_MISMATCH);
            }
            if (parent.getParentId() != null && parent.getParentId() > 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "最多支持两级分类");
            }
        }
        Category category = new Category();
        category.setUserId(userId);
        category.setParentId(parentId);
        category.setName(request.name().trim());
        category.setType(type);
        category.setIcon(request.icon());
        category.setColor(request.color() == null ? "#8C8C8C" : request.color());
        category.setSort(request.sort() == null ? 100 : request.sort());
        category.setIsSystem(0);
        categoryMapper.insert(category);
        return toVO(category, 0L);
    }

    @Override
    @Transactional
    public CategoryVO update(Long userId, Long id, CategoryRequest request) {
        Category category = getOwned(userId, id);
        if (request.name() != null && !request.name().isBlank()) {
            category.setName(request.name().trim());
        }
        if (request.icon() != null) {
            category.setIcon(request.icon());
        }
        if (request.color() != null) {
            category.setColor(request.color());
        }
        if (request.sort() != null) {
            category.setSort(request.sort());
        }
        categoryMapper.updateById(category);
        return toVO(category, 0L);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        Category category = getOwned(userId, id);
        if (Integer.valueOf(1).equals(category.getIsSystem())) {
            throw new BizException(ErrorCode.CATEGORY_SYSTEM_PROTECTED);
        }
        Long children = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getUserId, userId)
                .eq(Category::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }
        Long billCount = billMapper.selectCount(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .and(w -> w.eq(Bill::getCategoryId, id).or().eq(Bill::getParentCategoryId, id)));
        if (billCount != null && billCount > 0) {
            throw new BizException(ErrorCode.CATEGORY_HAS_BILLS, "该分类下有 " + billCount + " 笔账单，请先移动或删除");
        }
        categoryMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void initDefaultCategories(Long userId) {
        int sort = 0;
        int index = 0;
        for (CategoryTemplates.Group group : CategoryTemplates.EXPENSE) {
            createGroup(userId, group, BillType.EXPENSE, sort += 10, index++);
        }
        sort = 0;
        index = 0;
        for (CategoryTemplates.Group group : CategoryTemplates.INCOME) {
            createGroup(userId, group, BillType.INCOME, sort += 10, index++);
        }
    }

    /** 二级分类配色轮转表：让记账页的分类宫格在视觉上可区分 */
    private static final String[] CHILD_COLORS = {
            "#FF7A45", "#40A9FF", "#9254DE", "#36CFC9", "#F759AB",
            "#73D13D", "#597EF7", "#FFC53D", "#13C2C2", "#FAAD14"
    };

    private void createGroup(Long userId, CategoryTemplates.Group group, BillType type, int sort, int groupIndex) {
        Category parent = new Category();
        parent.setUserId(userId);
        parent.setParentId(0L);
        parent.setName(group.name());
        parent.setType(type.name());
        parent.setIcon(group.icon());
        parent.setColor(group.color());
        parent.setSort(sort);
        parent.setIsSystem(1);
        categoryMapper.insert(parent);

        int childSort = 0;
        int childIndex = 0;
        for (String childName : group.children()) {
            Category child = new Category();
            child.setUserId(userId);
            child.setParentId(parent.getId());
            child.setName(childName);
            child.setType(type.name());
            child.setIcon(group.icon());
            child.setColor(CHILD_COLORS[(groupIndex * 2 + childIndex) % CHILD_COLORS.length]);
            child.setSort(childSort += 10);
            child.setIsSystem(1);
            categoryMapper.insert(child);
            childIndex++;
        }
    }

    @Override
    public Category matchByKeyword(Long userId, String type, String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalizedType = StringUtils.hasText(type) ? type.toUpperCase() : BillType.EXPENSE.name();
        List<Category> candidates = listEntity(userId).stream()
                .filter(c -> normalizedType.equalsIgnoreCase(c.getType()))
                .toList();
        return CategoryMatcher.match(candidates, text);
    }

    private Map<Long, Long> billCountByCategory(Long userId) {
        List<Map<String, Object>> rows = billMapper.selectMaps(new QueryWrapper<Bill>()
                .select("category_id AS categoryId", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .groupBy("category_id"));
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object cid = row.get("categoryId");
            Object cnt = row.get("cnt");
            if (cid != null) {
                result.put(((Number) cid).longValue(), cnt == null ? 0L : ((Number) cnt).longValue());
            }
        }
        return result;
    }

    private CategoryVO toVO(Category category, Long billCount) {
        return new CategoryVO(
                category.getId(),
                category.getParentId(),
                category.getName(),
                category.getType(),
                category.getIcon(),
                category.getColor(),
                category.getSort(),
                Integer.valueOf(1).equals(category.getIsSystem()),
                billCount,
                List.of()
        );
    }

    /** 供 AI 记账使用：按名称精确查找 */
    public Category findByName(Long userId, String type, String name) {
        return listEntity(userId).stream()
                .filter(c -> c.getType().equalsIgnoreCase(type))
                .filter(c -> c.getName().equals(name))
                .min(Comparator.comparing(Category::getParentId))
                .orElse(null);
    }
}
