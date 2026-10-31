package com.jizhangbao.module.budget.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jizhangbao.common.enums.BillType;
import com.jizhangbao.common.exception.BizException;
import com.jizhangbao.common.exception.ErrorCode;
import com.jizhangbao.common.util.CacheKeys;
import com.jizhangbao.common.util.CacheHelper;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.module.bill.mapper.BillMapper;
import com.jizhangbao.module.budget.dto.BudgetRequest;
import com.jizhangbao.module.budget.dto.BudgetStatusVO;
import com.jizhangbao.module.budget.dto.BudgetVO;
import com.jizhangbao.module.budget.entity.Budget;
import com.jizhangbao.module.budget.mapper.BudgetMapper;
import com.jizhangbao.module.budget.service.BudgetService;
import com.jizhangbao.module.category.entity.Category;
import com.jizhangbao.module.category.service.CategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预算服务实现：预算进度计算 + 超支 / 临界提醒。
 */
@Service
public class BudgetServiceImpl implements BudgetService {

    private final BudgetMapper budgetMapper;
    private final BillMapper billMapper;
    private final CategoryService categoryService;
    private final CacheHelper cacheHelper;

    public BudgetServiceImpl(BudgetMapper budgetMapper, BillMapper billMapper,
                             CategoryService categoryService, CacheHelper cacheHelper) {
        this.budgetMapper = budgetMapper;
        this.billMapper = billMapper;
        this.categoryService = categoryService;
        this.cacheHelper = cacheHelper;
    }

    @Override
    public List<BudgetVO> list(Long userId, String yearMonth) {
        String month = normalizeMonth(yearMonth);
        List<Budget> budgets = budgetMapper.selectList(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getUserId, userId)
                .eq(Budget::getYearMonth, month)
                .orderByAsc(Budget::getCategoryId));
        Map<Long, Category> categoryMap = categoryService.listEntity(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity(), (a, b) -> a));
        BigDecimal monthExpense = totalUsedOf(userId, month);
        return budgets.stream()
                .map(budget -> toVO(budget, categoryMap, monthExpense, month))
                .toList();
    }

    @Override
    public BudgetStatusVO status(Long userId, String yearMonth) {
        String month = normalizeMonth(yearMonth);
        List<BudgetVO> items = list(userId, month);
        BigDecimal totalBudget = items.stream()
                .filter(item -> item.categoryId() != null && item.categoryId() == 0L)
                .map(BudgetVO::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        BigDecimal totalUsed = totalUsedOf(userId, month);
        BigDecimal remaining = totalBudget.subtract(totalUsed);
        int daysLeft = daysLeft(month);
        BigDecimal dailyAllowance = daysLeft <= 0 ? BigDecimal.ZERO
                : remaining.max(BigDecimal.ZERO).divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.HALF_UP);

        List<BudgetStatusVO.Alert> alerts = buildAlerts(userId, month, items, totalBudget, totalUsed);

        return new BudgetStatusVO(
                month,
                totalBudget,
                totalUsed,
                remaining,
                percent(totalUsed, totalBudget),
                dailyAllowance,
                daysLeft,
                items,
                alerts
        );
    }

    @Override
    @Transactional
    public BudgetVO create(Long userId, BudgetRequest request) {
        String month = normalizeMonth(request.yearMonth());
        Long categoryId = request.categoryId() == null ? 0L : request.categoryId();
        if (categoryId > 0) {
            Category category = categoryService.getOwned(userId, categoryId);
            if (!BillType.EXPENSE.name().equalsIgnoreCase(category.getType())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "只能为支出分类设置预算");
            }
        }
        Long exists = budgetMapper.selectCount(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getUserId, userId)
                .eq(Budget::getYearMonth, month)
                .eq(Budget::getCategoryId, categoryId));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.BUDGET_ALREADY_EXISTS);
        }
        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setYearMonth(month);
        budget.setCategoryId(categoryId);
        budget.setAmount(DateUtils.scale(request.amount()));
        budget.setAlertThreshold(request.alertThreshold() == null ? 80 : request.alertThreshold());
        budgetMapper.insert(budget);
        return toVO(budget, categoryService.listEntity(userId).stream()
                        .collect(Collectors.toMap(Category::getId, Function.identity(), (a, b) -> a)),
                totalUsedOf(userId, month), month);
    }

    @Override
    @Transactional
    public BudgetVO update(Long userId, Long id, BudgetRequest request) {
        Budget budget = getOwned(userId, id);
        if (request.amount() != null) {
            budget.setAmount(DateUtils.scale(request.amount()));
        }
        if (request.alertThreshold() != null) {
            budget.setAlertThreshold(request.alertThreshold());
        }
        budgetMapper.updateById(budget);
        Map<Long, Category> categoryMap = categoryService.listEntity(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity(), (a, b) -> a));
        return toVO(budget, categoryMap, totalUsedOf(userId, budget.getYearMonth()), budget.getYearMonth());
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        Budget budget = getOwned(userId, id);
        budgetMapper.deleteById(budget.getId());
    }

    @Override
    public BigDecimal totalBudgetOf(Long userId, String yearMonth) {
        Budget budget = budgetMapper.selectOne(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getUserId, userId)
                .eq(Budget::getYearMonth, normalizeMonth(yearMonth))
                .eq(Budget::getCategoryId, 0L)
                .last("LIMIT 1"));
        return budget == null ? BigDecimal.ZERO : budget.getAmount();
    }

    @Override
    public BigDecimal totalUsedOf(Long userId, String yearMonth) {
        LocalDate start = DateUtils.monthStart(yearMonth);
        LocalDate end = DateUtils.monthEnd(yearMonth);
        Map<String, Object> row = billMapper.sumRange(userId, start, end);
        if (row == null || row.get("expense") == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(row.get("expense").toString());
    }

    @Override
    @Transactional
    public int copyFromLastMonth(Long userId, String yearMonth) {
        String month = normalizeMonth(yearMonth);
        String previousMonth = MonthSupport.previous(month);
        List<Budget> previous = budgetMapper.selectList(new LambdaQueryWrapper<Budget>()
                .eq(Budget::getUserId, userId)
                .eq(Budget::getYearMonth, previousMonth));
        int copied = 0;
        for (Budget source : previous) {
            Long exists = budgetMapper.selectCount(new LambdaQueryWrapper<Budget>()
                    .eq(Budget::getUserId, userId)
                    .eq(Budget::getYearMonth, month)
                    .eq(Budget::getCategoryId, source.getCategoryId()));
            if (exists != null && exists > 0) {
                continue;
            }
            Budget target = new Budget();
            target.setUserId(userId);
            target.setYearMonth(month);
            target.setCategoryId(source.getCategoryId());
            target.setAmount(source.getAmount());
            target.setAlertThreshold(source.getAlertThreshold());
            budgetMapper.insert(target);
            copied++;
        }
        return copied;
    }

    private List<BudgetStatusVO.Alert> buildAlerts(Long userId, String month, List<BudgetVO> items,
                                                   BigDecimal totalBudget, BigDecimal totalUsed) {
        List<BudgetStatusVO.Alert> alerts = new ArrayList<>();
        if (totalBudget.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rate = percent(totalUsed, totalBudget);
            if (rate.compareTo(BigDecimal.valueOf(100)) >= 0) {
                alerts.add(new BudgetStatusVO.Alert("EXCEEDED", "本月预算已超支",
                        String.format("本月支出 %s 元，已超出预算 %s 元，建议控制后续开销。",
                                totalUsed, totalUsed.subtract(totalBudget)), 0L, totalUsed, rate));
            } else if (rate.compareTo(BigDecimal.valueOf(80)) >= 0) {
                alerts.add(new BudgetStatusVO.Alert("WARNING", "预算即将用完",
                        String.format("本月预算已使用 %s%%，剩余 %s 元，注意控制支出节奏。",
                                rate.stripTrailingZeros().toPlainString(),
                                totalBudget.subtract(totalUsed)), 0L, totalUsed, rate));
            }
        }
        for (BudgetVO item : items) {
            if (item.categoryId() == null || item.categoryId() == 0L) {
                continue;
            }
            int threshold = item.alertThreshold() == null ? 80 : item.alertThreshold();
            if (item.usageRate().compareTo(BigDecimal.valueOf(threshold)) >= 0
                    && item.amount().compareTo(BigDecimal.ZERO) > 0) {
                boolean exceeded = item.usageRate().compareTo(BigDecimal.valueOf(100)) >= 0;
                // 同一条分类预算提醒每天只出现一次，避免刷屏
                String key = CacheKeys.budgetAlert(userId, month, item.categoryId());
                boolean first = cacheHelper.setIfAbsent(key, Duration.ofHours(24));
                if (!first) {
                    continue;
                }
                alerts.add(new BudgetStatusVO.Alert(
                        exceeded ? "EXCEEDED" : "WARNING",
                        (exceeded ? "「" : "「") + item.categoryName() + (exceeded ? "」已超支" : "」接近预算上限"),
                        String.format("%s 预算 %s 元，已用 %s 元（%s%%）。",
                                item.categoryName(), item.amount(), item.used(),
                                item.usageRate().stripTrailingZeros().toPlainString()),
                        item.categoryId(),
                        item.used(),
                        item.usageRate()
                ));
            }
        }
        return alerts;
    }

    private BudgetVO toVO(Budget budget, Map<Long, Category> categoryMap,
                          BigDecimal monthExpense, String month) {
        Category category = budget.getCategoryId() != null && budget.getCategoryId() > 0
                ? categoryMap.get(budget.getCategoryId()) : null;
        BigDecimal used = budget.getCategoryId() == null || budget.getCategoryId() == 0
                ? monthExpense
                : billMapper.sumExpenseOfCategory(budget.getUserId(), budget.getCategoryId(),
                DateUtils.monthStart(month), DateUtils.monthEnd(month));
        if (used == null) {
            used = BigDecimal.ZERO;
        }
        BigDecimal rate = percent(used, budget.getAmount());
        int daysLeft = daysLeft(month);
        BigDecimal remaining = budget.getAmount().subtract(used);
        BigDecimal dailyAllowance = daysLeft <= 0 ? BigDecimal.ZERO
                : remaining.max(BigDecimal.ZERO).divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.HALF_UP);
        return new BudgetVO(
                budget.getId(),
                budget.getYearMonth(),
                budget.getCategoryId(),
                category == null ? "总预算" : category.getName(),
                category == null ? "wallet" : category.getIcon(),
                category == null ? "#1890FF" : category.getColor(),
                budget.getAmount(),
                used,
                remaining,
                rate,
                budget.getAlertThreshold(),
                statusOf(rate),
                dailyAllowance,
                daysLeft
        );
    }

    private String statusOf(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return "EXCEEDED";
        }
        if (rate.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "WARNING";
        }
        return "SAFE";
    }

    private BigDecimal percent(BigDecimal used, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return used.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    /** 剩余天数（不含今天已过），跨月返回当月天数 */
    private int daysLeft(String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        YearMonth current = YearMonth.now();
        if (yearMonth.isAfter(current)) {
            return yearMonth.lengthOfMonth();
        }
        if (yearMonth.isBefore(current)) {
            return 0;
        }
        return yearMonth.lengthOfMonth() - LocalDate.now().getDayOfMonth() + 1;
    }

    private String normalizeMonth(String yearMonth) {
        return (yearMonth == null || yearMonth.isBlank()) ? DateUtils.currentMonth()
                : DateUtils.parseMonth(yearMonth).format(DateUtils.YM);
    }

    private Budget getOwned(Long userId, Long id) {
        Budget budget = budgetMapper.selectById(id);
        if (budget == null) {
            throw new BizException(ErrorCode.BUDGET_NOT_FOUND);
        }
        if (!budget.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return budget;
    }

    /** 月份工具（避免额外依赖） */
    private static final class MonthSupport {
        static String previous(String month) {
            return YearMonth.parse(month).minusMonths(1).format(DateUtils.YM);
        }
    }
}
