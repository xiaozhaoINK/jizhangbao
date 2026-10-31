package com.jizhangbao.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jizhangbao.common.enums.BillType;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.module.bill.entity.Bill;
import com.jizhangbao.module.bill.mapper.BillMapper;
import com.jizhangbao.module.budget.service.BudgetService;
import com.jizhangbao.module.stats.dto.CategoryStatVO;
import com.jizhangbao.module.stats.dto.OverviewVO;
import com.jizhangbao.module.stats.service.StatsService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 把用户的账本数据整理成「大模型可读」的上下文。
 *
 * <p>这是让 AI 回答可信的关键：所有数字都来自真实库表聚合，模型只做表达，不做计算。</p>
 */
@Component
public class FinanceContextBuilder {

    private final StatsService statsService;
    private final BudgetService budgetService;
    private final BillMapper billMapper;

    public FinanceContextBuilder(StatsService statsService, BudgetService budgetService, BillMapper billMapper) {
        this.statsService = statsService;
        this.budgetService = budgetService;
        this.billMapper = billMapper;
    }

    public FinanceSnapshot build(Long userId, String yearMonth) {
        String month = (yearMonth == null || yearMonth.isBlank()) ? DateUtils.currentMonth() : yearMonth;
        OverviewVO overview = statsService.overview(userId, month);
        LocalDate start = DateUtils.monthStart(month);
        LocalDate end = DateUtils.monthEnd(month);

        List<FinanceSnapshot.CategoryLine> expenseLines =
                toLines(statsService.categoryStats(userId, start, end, BillType.EXPENSE.name(), "parent"));
        List<FinanceSnapshot.CategoryLine> incomeLines =
                toLines(statsService.categoryStats(userId, start, end, BillType.INCOME.name(), "parent"));

        BigDecimal budgetAmount = budgetService.totalBudgetOf(userId, month);
        BigDecimal budgetUsed = overview.monthExpense();
        BigDecimal budgetRate = budgetAmount.compareTo(BigDecimal.ZERO) > 0
                ? budgetUsed.multiply(BigDecimal.valueOf(100))
                .divide(budgetAmount, 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String topSingleBill = topSingleBill(userId, start, end);

        // 先构造结构化快照，再基于它渲染出提示词用的中文上下文
        FinanceSnapshot snapshot = new FinanceSnapshot(
                month,
                overview.monthIncome(),
                overview.monthExpense(),
                overview.monthBalance(),
                overview.monthBillCount() == null ? 0L : overview.monthBillCount(),
                overview.lastMonthIncome(),
                overview.lastMonthExpense(),
                overview.expenseMoM(),
                overview.avgDailyExpense(),
                overview.continuousDays() == null ? 0 : overview.continuousDays(),
                expenseLines,
                incomeLines,
                budgetAmount,
                budgetUsed,
                budgetRate,
                topSingleBill,
                null
        );
        return snapshot.withText(render(snapshot));
    }

    /** 渲染成中文上下文块（放入提示词） */
    private String render(FinanceSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("统计月份：").append(snapshot.yearMonth()).append('\n');
        sb.append("本月支出：").append(snapshot.monthExpense()).append(" 元\n");
        sb.append("本月收入：").append(snapshot.monthIncome()).append(" 元\n");
        sb.append("本月结余：").append(snapshot.balance()).append(" 元\n");
        sb.append("本月账单笔数：").append(snapshot.billCount()).append(" 笔\n");
        sb.append("日均支出：").append(snapshot.avgDailyExpense()).append(" 元\n");
        sb.append("连续记账：").append(snapshot.continuousDays()).append(" 天\n");
        if (snapshot.lastMonthExpense() != null && snapshot.lastMonthExpense().compareTo(BigDecimal.ZERO) > 0) {
            sb.append("上月支出：").append(snapshot.lastMonthExpense()).append(" 元，");
            sb.append("环比变化：").append(snapshot.expenseMoM()).append("%\n");
        } else {
            sb.append("上月支出：暂无数据\n");
        }
        sb.append("支出分类排行：");
        if (snapshot.topExpenseCategories().isEmpty()) {
            sb.append("暂无数据");
        } else {
            snapshot.topExpenseCategories().forEach(line -> sb.append(String.format(
                    "%s %s 元（占比 %s%%，%d 笔）、", line.name(), line.amount(), line.percent(), line.count())));
            sb.setLength(sb.length() - 1);
        }
        sb.append('\n');
        sb.append("收入分类排行：");
        if (snapshot.topIncomeCategories().isEmpty()) {
            sb.append("暂无数据");
        } else {
            snapshot.topIncomeCategories().forEach(line -> sb.append(String.format(
                    "%s %s 元、", line.name(), line.amount())));
            sb.setLength(sb.length() - 1);
        }
        sb.append('\n');
        sb.append("本月预算：");
        if (snapshot.budgetAmount().compareTo(BigDecimal.ZERO) > 0) {
            sb.append(snapshot.budgetAmount()).append(" 元，已用 ")
                    .append(snapshot.budgetUsed()).append(" 元，使用率 ")
                    .append(snapshot.budgetUsageRate()).append("%\n");
        } else {
            sb.append("未设置\n");
        }
        if (snapshot.topSingleBill() != null) {
            sb.append("本月最大单笔支出：").append(snapshot.topSingleBill()).append('\n');
        }
        return sb.toString();
    }

    private List<FinanceSnapshot.CategoryLine> toLines(List<CategoryStatVO> stats) {
        return stats.stream()
                .limit(6)
                .map(stat -> new FinanceSnapshot.CategoryLine(
                        stat.name(), stat.amount(), stat.percent(), stat.count() == null ? 0L : stat.count()))
                .toList();
    }

    private String topSingleBill(Long userId, LocalDate start, LocalDate end) {
        Bill bill = billMapper.selectOne(new LambdaQueryWrapper<Bill>()
                .eq(Bill::getUserId, userId)
                .eq(Bill::getType, BillType.EXPENSE.name())
                .between(Bill::getBillDate, start, end)
                .orderByDesc(Bill::getAmount)
                .last("LIMIT 1"));
        if (bill == null) {
            return null;
        }
        String remark = bill.getRemark() == null || bill.getRemark().isBlank() ? "一笔支出" : bill.getRemark();
        return remark + " " + bill.getAmount() + " 元（" + bill.getBillDate() + "）";
    }
}
