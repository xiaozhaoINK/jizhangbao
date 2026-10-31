package com.jizhangbao.module.stats.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.jizhangbao.common.enums.BillType;
import com.jizhangbao.common.util.CacheHelper;
import com.jizhangbao.common.util.CacheKeys;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.module.bill.mapper.BillMapper;
import com.jizhangbao.module.budget.service.BudgetService;
import com.jizhangbao.module.stats.dto.CategoryStatVO;
import com.jizhangbao.module.stats.dto.DayStatVO;
import com.jizhangbao.module.stats.dto.MonthCompareVO;
import com.jizhangbao.module.stats.dto.OverviewVO;
import com.jizhangbao.module.stats.dto.TrendPointVO;
import com.jizhangbao.module.stats.service.StatsService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 统计分析实现。
 *
 * <p>聚合全部下推给 MySQL（<code>GROUP BY</code>），Java 层只负责补齐连续坐标轴与计算环比/占比，
 * 避免把明细数据拉到应用侧再计算。</p>
 */
@Service
public class StatsServiceImpl implements StatsService {

    private final BillMapper billMapper;
    private final BudgetService budgetService;
    private final CacheHelper cacheHelper;

    public StatsServiceImpl(BillMapper billMapper, BudgetService budgetService, CacheHelper cacheHelper) {
        this.billMapper = billMapper;
        this.budgetService = budgetService;
        this.cacheHelper = cacheHelper;
    }

    @Override
    public OverviewVO overview(Long userId, String yearMonth) {
        String month = (yearMonth == null || yearMonth.isBlank()) ? DateUtils.currentMonth() : yearMonth;
        return cacheHelper.rememberJson(
                CacheKeys.statsOverview(userId, month),
                Duration.ofSeconds(60),
                new TypeReference<OverviewVO>() {
                },
                () -> buildOverview(userId, month));
    }

    private OverviewVO buildOverview(Long userId, String month) {
        LocalDate start = DateUtils.monthStart(month);
        LocalDate end = DateUtils.monthEnd(month);
        LocalDate today = LocalDate.now();

        Months monthSum = sumOf(userId, start, end);
        Months todaySum = sumOf(userId, today, today);
        LocalDate lastStart = start.minusMonths(1);
        Months lastSum = sumOf(userId, lastStart, lastStart.withDayOfMonth(lastStart.lengthOfMonth()));

        // 日均支出：当月按已过去的天数计算，历史月份按整月天数计算
        YearMonth targetMonth = YearMonth.from(start);
        YearMonth nowMonth = YearMonth.from(today);
        int elapsedDays;
        if (targetMonth.equals(nowMonth)) {
            elapsedDays = today.getDayOfMonth();
        } else if (targetMonth.isAfter(nowMonth)) {
            elapsedDays = 1;
        } else {
            elapsedDays = targetMonth.lengthOfMonth();
        }
        elapsedDays = Math.max(1, elapsedDays);
        BigDecimal avgDaily = monthSum.expense.divide(BigDecimal.valueOf(elapsedDays), 2, RoundingMode.HALF_UP);

        List<CategoryStatVO> categories = categoryStats(userId, start, end, BillType.EXPENSE.name(), "parent");
        CategoryStatVO top = categories.isEmpty() ? null : categories.get(0);

        BigDecimal budget = budgetService.totalBudgetOf(userId, month);
        BigDecimal budgetUsed = monthSum.expense;
        BigDecimal usageRate = budget.compareTo(BigDecimal.ZERO) > 0
                ? budgetUsed.multiply(BigDecimal.valueOf(100)).divide(budget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new OverviewVO(
                month,
                monthSum.income,
                monthSum.expense,
                monthSum.income.subtract(monthSum.expense),
                monthSum.count,
                todaySum.income,
                todaySum.expense,
                todaySum.count,
                lastSum.income,
                lastSum.expense,
                mom(monthSum.expense, lastSum.expense),
                mom(monthSum.income, lastSum.income),
                avgDaily,
                top == null ? null : top.name(),
                top == null ? BigDecimal.ZERO : top.amount(),
                budget,
                budgetUsed,
                usageRate,
                continuousDays(userId)
        );
    }

    @Override
    public List<TrendPointVO> trend(Long userId, LocalDate start, LocalDate end, String granularity) {
        LocalDate from = start == null ? LocalDate.now().withDayOfMonth(1) : start;
        LocalDate to = end == null ? LocalDate.now() : end;
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        boolean byMonth = "month".equalsIgnoreCase(granularity)
                || (to.toEpochDay() - from.toEpochDay()) > 120;

        List<Map<String, Object>> rows = byMonth
                ? billMapper.sumByMonth(userId, DateUtils.toMonth(from), DateUtils.toMonth(to))
                : billMapper.sumByDay(userId, from, to, null);

        Map<String, TrendPointVO> indexed = new LinkedHashMap<>();
        if (byMonth) {
            for (String month : DateUtils.monthsBetween(DateUtils.toMonth(from), DateUtils.toMonth(to))) {
                indexed.put(month, new TrendPointVO(month, month.substring(5) + "月",
                        BigDecimal.ZERO, BigDecimal.ZERO));
            }
            for (Map<String, Object> row : rows) {
                String key = String.valueOf(row.get("month"));
                indexed.put(key, new TrendPointVO(key, key.substring(5) + "月",
                        decimal(row.get("income")), decimal(row.get("expense"))));
            }
        } else {
            for (LocalDate date : DateUtils.datesBetween(from, to)) {
                String key = date.toString();
                indexed.put(key, new TrendPointVO(key, key.substring(5), BigDecimal.ZERO, BigDecimal.ZERO));
            }
            for (Map<String, Object> row : rows) {
                Object day = row.get("day");
                String key = day instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate().toString()
                        : String.valueOf(day);
                indexed.put(key, new TrendPointVO(key, key.length() >= 10 ? key.substring(5) : key,
                        decimal(row.get("income")), decimal(row.get("expense"))));
            }
        }
        return new ArrayList<>(indexed.values());
    }

    @Override
    public List<CategoryStatVO> categoryStats(Long userId, LocalDate start, LocalDate end,
                                              String type, String level) {
        String billType = StringUtils.hasText(type) ? type.toUpperCase() : BillType.EXPENSE.name();
        String groupLevel = "child".equalsIgnoreCase(level) ? "child" : "parent";
        LocalDate from = start == null ? LocalDate.now().withDayOfMonth(1) : start;
        LocalDate to = end == null ? LocalDate.now() : end;

        List<Map<String, Object>> rows = billMapper.sumByCategory(userId, from, to, billType, groupLevel);
        BigDecimal total = rows.stream()
                .map(row -> decimal(row.get("amount")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategoryStatVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            BigDecimal amount = decimal(row.get("amount"));
            long count = row.get("cnt") == null ? 0L : ((Number) row.get("cnt")).longValue();
            BigDecimal percent = total.compareTo(BigDecimal.ZERO) > 0
                    ? amount.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal avg = count > 0 ? amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Object rawId = row.get("categoryId");
            result.add(new CategoryStatVO(
                    rawId == null ? 0L : ((Number) rawId).longValue(),
                    row.get("categoryName") == null ? "未分类" : String.valueOf(row.get("categoryName")),
                    row.get("icon") == null ? "other" : String.valueOf(row.get("icon")),
                    row.get("color") == null ? "#8C8C8C" : String.valueOf(row.get("color")),
                    amount,
                    count,
                    percent,
                    avg
            ));
        }
        return result;
    }

    @Override
    public List<MonthCompareVO> monthlyCompare(Long userId, int year) {
        int targetYear = year <= 0 ? LocalDate.now().getYear() : year;
        List<Map<String, Object>> rows = billMapper.sumByMonth(userId,
                targetYear + "-01", targetYear + "-12");
        Map<String, MonthCompareVO> indexed = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            String key = String.format("%04d-%02d", targetYear, m);
            indexed.put(key, new MonthCompareVO(key, m + "月", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        for (Map<String, Object> row : rows) {
            String key = String.valueOf(row.get("month"));
            BigDecimal income = decimal(row.get("income"));
            BigDecimal expense = decimal(row.get("expense"));
            indexed.put(key, new MonthCompareVO(key, Integer.parseInt(key.substring(5)) + "月",
                    income, expense, income.subtract(expense)));
        }
        return new ArrayList<>(indexed.values());
    }

    @Override
    public List<DayStatVO> calendar(Long userId, String yearMonth) {
        String month = (yearMonth == null || yearMonth.isBlank()) ? DateUtils.currentMonth() : yearMonth;
        LocalDate start = DateUtils.monthStart(month);
        LocalDate end = DateUtils.monthEnd(month);
        List<Map<String, Object>> rows = billMapper.sumByDay(userId, start, end, null);
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object day = row.get("day");
            String key = day instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate().toString()
                    : String.valueOf(day);
            byDate.put(key, row);
        }
        BigDecimal max = byDate.values().stream()
                .map(row -> decimal(row.get("expense")))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        List<DayStatVO> result = new ArrayList<>();
        for (LocalDate date : DateUtils.datesBetween(start, end)) {
            Map<String, Object> row = byDate.get(date.toString());
            BigDecimal income = row == null ? BigDecimal.ZERO : decimal(row.get("income"));
            BigDecimal expense = row == null ? BigDecimal.ZERO : decimal(row.get("expense"));
            long count = income.add(expense).compareTo(BigDecimal.ZERO) > 0 ? 1L : 0L;
            int level = 0;
            if (row != null && max.compareTo(BigDecimal.ZERO) > 0) {
                double ratio = expense.doubleValue() / max.doubleValue();
                level = ratio <= 0 ? 0 : Math.min(4, (int) Math.ceil(ratio * 4));
            }
            result.add(new DayStatVO(date.toString(), date.getDayOfMonth(), income, expense, count, level));
        }
        return result;
    }

    @Override
    public List<MonthCompareVO> recentMonths(Long userId, int months) {
        int count = Math.max(1, Math.min(months, 24));
        YearMonth current = YearMonth.now();
        String startMonth = current.minusMonths(count - 1L).format(DateUtils.YM);
        String endMonth = current.format(DateUtils.YM);
        List<Map<String, Object>> rows = billMapper.sumByMonth(userId, startMonth, endMonth);
        Map<String, Map<String, Object>> byMonth = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            byMonth.put(String.valueOf(row.get("month")), row);
        }
        List<MonthCompareVO> result = new ArrayList<>();
        for (String month : DateUtils.monthsBetween(startMonth, endMonth)) {
            Map<String, Object> row = byMonth.get(month);
            BigDecimal income = row == null ? BigDecimal.ZERO : decimal(row.get("income"));
            BigDecimal expense = row == null ? BigDecimal.ZERO : decimal(row.get("expense"));
            result.add(new MonthCompareVO(month, Integer.parseInt(month.substring(5)) + "月",
                    income, expense, income.subtract(expense)));
        }
        return result;
    }

    /** 连续记账天数 */
    private int continuousDays(Long userId) {
        List<LocalDate> dates = billMapper.recentBillDates(userId);
        if (dates == null || dates.isEmpty()) {
            return 0;
        }
        Set<LocalDate> set = new HashSet<>(dates);
        LocalDate cursor = LocalDate.now();
        if (!set.contains(cursor)) {
            cursor = cursor.minusDays(1);
            if (!set.contains(cursor)) {
                return 0;
            }
        }
        int days = 0;
        while (set.contains(cursor)) {
            days++;
            cursor = cursor.minusDays(1);
        }
        return days;
    }

    private Months sumOf(Long userId, LocalDate start, LocalDate end) {
        Map<String, Object> row = billMapper.sumRange(userId, start, end);
        Months months = new Months();
        if (row != null) {
            months.income = decimal(row.get("income"));
            months.expense = decimal(row.get("expense"));
            Object cnt = row.get("cnt");
            months.count = cnt == null ? 0L : ((Number) cnt).longValue();
        }
        return months;
    }

    private BigDecimal mom(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private static final class Months {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        long count = 0L;
    }
}
