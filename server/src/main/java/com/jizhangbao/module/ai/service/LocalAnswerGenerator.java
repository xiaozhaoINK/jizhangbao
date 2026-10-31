package com.jizhangbao.module.ai.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 本地问答生成器（规则模式下的「小账」）。
 *
 * <p>不调用任何外部服务，仅基于真实账本快照做意图识别 + 模板化表达，
 * 因此即使在无网络 / 无 API Key 的环境下，AI 助手也能给出有数据支撑的回答。</p>
 */
@Component
public class LocalAnswerGenerator {

    public String answer(FinanceSnapshot snapshot, String question) {
        String q = question == null ? "" : question;
        if (!StringUtils.hasText(q)) {
            return summary(snapshot);
        }
        if (containsAny(q, "预算", "超支", "还剩多少额度", "预算还剩")) {
            return budget(snapshot);
        }
        if (containsAny(q, "省", "建议", "优化", "控制", "怎么才能", "有什么办法")) {
            return suggestions(snapshot);
        }
        if (containsAny(q, "环比", "上月", "对比", "比以前", "趋势", "变化")) {
            return comparison(snapshot);
        }
        if (containsAny(q, "收入", "赚", "工资", "进账")) {
            return income(snapshot);
        }
        if (containsAny(q, "结余", "存", "剩", "攒")) {
            return balance(snapshot);
        }
        if (containsAny(q, "最多", "最高", "占比", "分类", "哪一类", "花在哪", "大头")) {
            return categories(snapshot);
        }
        if (containsAny(q, "今天", "今日", "日均", "平均")) {
            return daily(snapshot);
        }
        if (containsAny(q, "花", "支出", "消费", "多少")) {
            return expense(snapshot);
        }
        return summary(snapshot);
    }

    private String expense(FinanceSnapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("你 ").append(s.yearMonth()).append(" 一共花了 ").append(money(s.monthExpense()))
                .append(" 元，共 ").append(s.billCount()).append(" 笔。");
        if (!s.topExpenseCategories().isEmpty()) {
            FinanceSnapshot.CategoryLine top = s.topExpenseCategories().get(0);
            sb.append("花得最多的是「").append(top.name()).append("」，")
                    .append(money(top.amount())).append(" 元，占 ")
                    .append(percent(top.percent())).append("。");
        }
        sb.append("日均大约 ").append(money(s.avgDailyExpense())).append(" 元。");
        return sb.toString();
    }

    private String income(FinanceSnapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("本月收入 ").append(money(s.monthIncome())).append(" 元。");
        if (!s.topIncomeCategories().isEmpty()) {
            sb.append("主要来源：");
            List<FinanceSnapshot.CategoryLine> lines = s.topIncomeCategories();
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                FinanceSnapshot.CategoryLine line = lines.get(i);
                sb.append(line.name()).append(" ").append(money(line.amount())).append(" 元");
                if (i < Math.min(3, lines.size()) - 1) {
                    sb.append("、");
                }
            }
            sb.append("。");
        }
        sb.append("结余 ").append(money(s.balance())).append(" 元。");
        return sb.toString();
    }

    private String balance(FinanceSnapshot s) {
        String tip = s.balance().compareTo(BigDecimal.ZERO) >= 0
                ? "继续保持这个节奏，年底就能存下一笔钱。"
                : "支出超过了收入，建议先看一下支出大头，把非必要消费压一压。";
        return "本月结余 " + money(s.balance()) + " 元（收入 " + money(s.monthIncome())
                + " 元 − 支出 " + money(s.monthExpense()) + " 元）。" + tip;
    }

    private String categories(FinanceSnapshot s) {
        if (s.topExpenseCategories().isEmpty()) {
            return "这个月还没有支出记录，记上几笔我就能帮你分析消费结构了。";
        }
        StringBuilder sb = new StringBuilder("本月支出结构：\n");
        List<FinanceSnapshot.CategoryLine> lines = s.topExpenseCategories();
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            FinanceSnapshot.CategoryLine line = lines.get(i);
            sb.append("- ").append(line.name()).append("：").append(money(line.amount()))
                    .append(" 元（").append(percent(line.percent())).append("，")
                    .append(line.count()).append(" 笔）\n");
        }
        FinanceSnapshot.CategoryLine top = lines.get(0);
        sb.append("「").append(top.name()).append("」是最大的支出项，想省钱的优先从这里下手。");
        return sb.toString().trim();
    }

    private String budget(FinanceSnapshot s) {
        if (s.budgetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return "你本月还没有设置预算。建议先设一个月度总预算，我可以每天帮你盯着进度。";
        }
        BigDecimal remain = s.budgetAmount().subtract(s.budgetUsed());
        StringBuilder sb = new StringBuilder();
        sb.append("本月预算 ").append(money(s.budgetAmount())).append(" 元，已用 ")
                .append(money(s.budgetUsed())).append(" 元，使用率 ")
                .append(percent(s.budgetUsageRate())).append("。");
        if (s.budgetUsageRate().compareTo(BigDecimal.valueOf(100)) >= 0) {
            sb.append("已经超支 ").append(money(remain.negate())).append(" 元，接下来尽量只花必需的钱。");
        } else if (s.budgetUsageRate().compareTo(BigDecimal.valueOf(80)) >= 0) {
            sb.append("只剩 ").append(money(remain)).append(" 元了，注意控制节奏。");
        } else {
            sb.append("还剩 ").append(money(remain)).append(" 元，节奏很健康。");
        }
        return sb.toString();
    }

    private String comparison(FinanceSnapshot s) {
        if (s.lastMonthExpense() == null || s.lastMonthExpense().compareTo(BigDecimal.ZERO) <= 0) {
            return "账本里还没有上个月的数据，等下个月我就能给你做环比对比了。本月当前支出 "
                    + money(s.monthExpense()) + " 元。";
        }
        BigDecimal diff = s.monthExpense().subtract(s.lastMonthExpense());
        String direction = diff.compareTo(BigDecimal.ZERO) >= 0 ? "多" : "少";
        return "本月支出 " + money(s.monthExpense()) + " 元，上月 " + money(s.lastMonthExpense())
                + " 元，环比" + (s.expenseMoM().compareTo(BigDecimal.ZERO) >= 0 ? "上升 " : "下降 ")
                + percent(s.expenseMoM().abs()) + "，" + direction + "花了 " + money(diff.abs()) + " 元。";
    }

    private String daily(FinanceSnapshot s) {
        return "本月日均支出 " + money(s.avgDailyExpense()) + " 元，已经连续记账 "
                + s.continuousDays() + " 天。按这个日均速度，本月支出预计在 "
                + money(s.avgDailyExpense().multiply(BigDecimal.valueOf(30))) + " 元左右。";
    }

    private String suggestions(FinanceSnapshot s) {
        if (s.topExpenseCategories().isEmpty()) {
            return "先记几笔账吧，有了数据我才能给出针对性的省钱建议。";
        }
        StringBuilder sb = new StringBuilder("结合你这个月的账本，给你 3 条建议：\n");
        FinanceSnapshot.CategoryLine top = pickSavableCategory(s);
        BigDecimal cut = top.amount().multiply(BigDecimal.valueOf(0.2)).setScale(2, RoundingMode.HALF_UP);
        sb.append("- 「").append(top.name()).append("」是本月可优化的最大支出（").append(money(top.amount()))
                .append(" 元），压缩 20% 就能省下 ").append(money(cut)).append(" 元。\n");
        if (s.budgetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            sb.append("- 建议先设一个月度预算，比如 ").append(money(s.monthExpense().max(BigDecimal.valueOf(1000))))
                    .append(" 元，超支时我能及时提醒你。\n");
        } else {
            sb.append("- 预算已用 ").append(percent(s.budgetUsageRate()))
                    .append("，把剩下 ").append(money(s.budgetAmount().subtract(s.budgetUsed())))
                    .append(" 元均摊到每天，单日别超过 ")
                    .append(money(s.budgetAmount().subtract(s.budgetUsed())
                            .divide(BigDecimal.valueOf(10), 2, RoundingMode.HALF_UP))).append(" 元。\n");
        }
        sb.append("- 每周固定一天做「无消费日」，按日均 ").append(money(s.avgDailyExpense()))
                .append(" 元算，一个月能省 ").append(money(s.avgDailyExpense().multiply(BigDecimal.valueOf(4))))
                .append(" 元左右。");
        return sb.toString();
    }

    private String summary(FinanceSnapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.yearMonth()).append(" 账本小结：收入 ").append(money(s.monthIncome()))
                .append(" 元，支出 ").append(money(s.monthExpense()))
                .append(" 元，结余 ").append(money(s.balance())).append(" 元，共 ")
                .append(s.billCount()).append(" 笔。");
        if (!s.topExpenseCategories().isEmpty()) {
            FinanceSnapshot.CategoryLine top = s.topExpenseCategories().get(0);
            sb.append("支出大头是「").append(top.name()).append("」（")
                    .append(money(top.amount())).append(" 元，占 ")
                    .append(percent(top.percent())).append("）。");
        }
        sb.append("你可以问我：本月花了多少、哪类花得最多、预算还剩多少、怎么省。");
        return sb.toString();
    }

    /** 刚性支出（房租/水电/还款等）不适合建议"砍两成"，这里挑一个可优化的类别 */
    private FinanceSnapshot.CategoryLine pickSavableCategory(FinanceSnapshot s) {
        List<String> rigid = List.of("居住", "房租", "水电燃气", "物业费", "宽带通讯", "其他支出", "其他");
        for (FinanceSnapshot.CategoryLine line : s.topExpenseCategories()) {
            if (!rigid.contains(line.name()) && line.amount().compareTo(BigDecimal.ZERO) > 0) {
                return line;
            }
        }
        return s.topExpenseCategories().get(0);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String percent(BigDecimal value) {
        return value == null ? "0%" : value.stripTrailingZeros().toPlainString() + "%";
    }
}
