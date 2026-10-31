package com.jizhangbao.module.bill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jizhangbao.common.api.PageResult;
import com.jizhangbao.common.enums.BillSource;
import com.jizhangbao.common.enums.BillType;
import com.jizhangbao.common.exception.BizException;
import com.jizhangbao.common.exception.ErrorCode;
import com.jizhangbao.common.util.CacheKeys;
import com.jizhangbao.common.util.CacheHelper;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.module.bill.dto.BillQuery;
import com.jizhangbao.module.bill.dto.BillRequest;
import com.jizhangbao.module.bill.dto.BillSummaryVO;
import com.jizhangbao.module.bill.dto.BillVO;
import com.jizhangbao.module.bill.entity.Bill;
import com.jizhangbao.module.bill.mapper.BillMapper;
import com.jizhangbao.module.bill.service.BillService;
import com.jizhangbao.module.category.entity.Category;
import com.jizhangbao.module.category.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 账单服务实现。
 */
@Service
public class BillServiceImpl implements BillService {

    private final BillMapper billMapper;
    private final CategoryService categoryService;
    private final CacheHelper cacheHelper;

    public BillServiceImpl(BillMapper billMapper, CategoryService categoryService, CacheHelper cacheHelper) {
        this.billMapper = billMapper;
        this.categoryService = categoryService;
        this.cacheHelper = cacheHelper;
    }

    @Override
    public PageResult<BillVO> page(Long userId, BillQuery query) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .ge(query.getStartDate() != null, Bill::getBillDate, query.getStartDate())
                .le(query.getEndDate() != null, Bill::getBillDate, query.getEndDate())
                .eq(StringUtils.hasText(query.getType()), Bill::getType,
                        query.getType() == null ? null : query.getType().toUpperCase())
                .eq(StringUtils.hasText(query.getPayMethod()), Bill::getPayMethod, query.getPayMethod())
                .ge(query.getMinAmount() != null, Bill::getAmount, query.getMinAmount())
                .le(query.getMaxAmount() != null, Bill::getAmount, query.getMaxAmount());

        if (query.getCategoryId() != null) {
            Long categoryId = query.getCategoryId();
            wrapper.and(w -> w.eq(Bill::getCategoryId, categoryId)
                    .or().eq(Bill::getParentCategoryId, categoryId));
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Bill::getRemark, keyword)
                    .or().like(Bill::getTags, keyword)
                    .or().like(Bill::getAiInput, keyword));
        }

        boolean asc = "asc".equalsIgnoreCase(query.getSortOrder());
        switch (query.getSortBy()) {
            case "amount" -> wrapper.orderBy(true, asc, Bill::getAmount);
            case "createdAt" -> wrapper.orderBy(true, asc, Bill::getCreatedAt);
            default -> wrapper.orderBy(true, asc, Bill::getBillDate).orderBy(true, asc, Bill::getId);
        }

        Page<Bill> page = billMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        List<BillVO> records = toVOList(page.getRecords(), userId);
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public BillVO detail(Long userId, Long id) {
        Bill bill = getOwned(userId, id);
        return toVOList(List.of(bill), userId).get(0);
    }

    @Override
    @Transactional
    public BillVO create(Long userId, BillRequest request) {
        Bill bill = new Bill();
        bill.setUserId(userId);
        bill.setType(request.type().toUpperCase());
        applyCategory(bill, userId, request.categoryId(), bill.getType());
        bill.setAmount(DateUtils.scale(request.amount()));
        bill.setRemark(request.remark());
        bill.setBillDate(request.billDate() == null ? LocalDate.now() : request.billDate());
        bill.setBillTime(LocalDateTime.now());
        bill.setPayMethod(request.payMethod());
        bill.setTags(request.tags());
        bill.setSource(BillSource.MANUAL.name());
        billMapper.insert(bill);
        invalidateStats(userId);
        return toVOList(List.of(bill), userId).get(0);
    }

    @Override
    @Transactional
    public BillVO update(Long userId, Long id, BillRequest request) {
        Bill bill = getOwned(userId, id);
        String type = request.type() == null ? bill.getType() : request.type().toUpperCase();
        bill.setType(type);
        applyCategory(bill, userId, request.categoryId(), type);
        if (request.amount() != null) {
            bill.setAmount(DateUtils.scale(request.amount()));
        }
        if (request.remark() != null) {
            bill.setRemark(request.remark());
        }
        if (request.billDate() != null) {
            bill.setBillDate(request.billDate());
        }
        if (request.payMethod() != null) {
            bill.setPayMethod(request.payMethod());
        }
        if (request.tags() != null) {
            bill.setTags(request.tags());
        }
        billMapper.updateById(bill);
        invalidateStats(userId);
        return toVOList(List.of(bill), userId).get(0);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        Bill bill = getOwned(userId, id);
        billMapper.deleteById(bill.getId());
        invalidateStats(userId);
    }

    @Override
    public BillSummaryVO summary(Long userId, LocalDate start, LocalDate end) {
        Map<String, Object> row = billMapper.sumRange(userId, start, end);
        if (row == null) {
            return new BillSummaryVO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
        }
        BigDecimal income = toDecimal(row.get("income"));
        BigDecimal expense = toDecimal(row.get("expense"));
        Object cnt = row.get("cnt");
        return new BillSummaryVO(income, expense, income.subtract(expense),
                cnt == null ? 0L : ((Number) cnt).longValue());
    }

    @Override
    public List<BillVO> recent(Long userId, int limit) {
        List<Bill> bills = billMapper.selectList(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .orderByDesc(Bill::getBillDate)
                .orderByDesc(Bill::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 50))));
        return toVOList(bills, userId);
    }

    @Override
    @Transactional
    public Bill createFromAi(Long userId, Long categoryId, String type, BigDecimal amount,
                             LocalDate billDate, String remark, String payMethod, String rawInput) {
        Bill bill = new Bill();
        bill.setUserId(userId);
        bill.setType(type.toUpperCase());
        applyCategory(bill, userId, categoryId, bill.getType());
        bill.setAmount(DateUtils.scale(amount));
        bill.setRemark(remark);
        bill.setBillDate(billDate == null ? LocalDate.now() : billDate);
        bill.setBillTime(LocalDateTime.now());
        bill.setPayMethod(payMethod);
        bill.setSource(BillSource.AI.name());
        bill.setAiInput(rawInput);
        billMapper.insert(bill);
        invalidateStats(userId);
        return bill;
    }

    @Override
    public BillVO toVO(Bill bill, Map<Long, Category> categoryMap) {
        Category child = categoryMap.get(bill.getCategoryId());
        Category parent = categoryMap.get(bill.getParentCategoryId());
        return new BillVO(
                bill.getId(),
                bill.getType(),
                bill.getCategoryId(),
                child == null ? "未分类" : child.getName(),
                child == null ? "other" : child.getIcon(),
                child == null ? "#8C8C8C" : child.getColor(),
                bill.getParentCategoryId(),
                parent == null ? null : parent.getName(),
                bill.getAmount(),
                bill.getRemark(),
                bill.getBillDate(),
                bill.getBillTime(),
                bill.getPayMethod(),
                bill.getTags(),
                bill.getSource(),
                bill.getAiInput(),
                bill.getCreatedAt()
        );
    }

    @Override
    public List<BillVO> toVOList(List<Bill> bills, Long userId) {
        Map<Long, Category> categoryMap = categoryService.listEntity(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity(), (a, b) -> a));
        return bills.stream().map(bill -> toVO(bill, categoryMap)).toList();
    }

    @Override
    public long countByUser(Long userId) {
        Long count = billMapper.selectCount(new LambdaQueryWrapper<Bill>().eq(Bill::getUserId, userId));
        return count == null ? 0L : count;
    }

    private Bill getOwned(Long userId, Long id) {
        Bill bill = billMapper.selectById(id);
        if (bill == null) {
            throw new BizException(ErrorCode.BILL_NOT_FOUND);
        }
        if (!bill.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.BILL_NOT_OWNED);
        }
        return bill;
    }

    private void applyCategory(Bill bill, Long userId, Long categoryId, String type) {
        Category category = categoryService.getOwned(userId, categoryId);
        if (!category.getType().equalsIgnoreCase(type)) {
            throw new BizException(ErrorCode.CATEGORY_TYPE_MISMATCH);
        }
        bill.setCategoryId(category.getId());
        bill.setParentCategoryId(category.getParentId() == null || category.getParentId() == 0
                ? category.getId() : category.getParentId());
    }

    private void invalidateStats(Long userId) {
        cacheHelper.deleteByPrefix("jzb:stats:overview:" + userId + ":");
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    /** 供概览缓存 key 复用 */
    public static String overviewCacheKey(Long userId, String dimension) {
        return CacheKeys.statsOverview(userId, dimension);
    }

    /** 工具：按天分组填充空日期（趋势图需要连续坐标轴） */
    public static Map<String, Object[]> emptyDayMap() {
        return new HashMap<>();
    }
}
