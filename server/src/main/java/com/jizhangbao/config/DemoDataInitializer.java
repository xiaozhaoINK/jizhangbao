package com.jizhangbao.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jizhangbao.common.enums.BillSource;
import com.jizhangbao.common.enums.BillType;
import com.jizhangbao.common.enums.UserRole;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.module.bill.entity.Bill;
import com.jizhangbao.module.bill.mapper.BillMapper;
import com.jizhangbao.module.budget.entity.Budget;
import com.jizhangbao.module.budget.mapper.BudgetMapper;
import com.jizhangbao.module.category.entity.Category;
import com.jizhangbao.module.category.service.CategoryService;
import com.jizhangbao.module.report.service.MonthlyReportService;
import com.jizhangbao.module.user.entity.User;
import com.jizhangbao.module.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 演示数据初始化：首次启动时自动创建演示账号与近半年的真实感账单，
 * 让 cloned 项目「打开就有数据可看」，也方便截图与录屏。
 *
 * <p>幂等：已存在演示账单时直接跳过，不会重复写入。</p>
 */
@Component
@ConditionalOnProperty(name = "jizhangbao.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("M月d日");

    private static final List<String> PAY_METHODS = List.of(
            "微信", "支付宝", "微信", "支付宝", "银行卡", "信用卡", "现金");

    private final AppProperties properties;
    private final UserService userService;
    private final CategoryService categoryService;
    private final BillMapper billMapper;
    private final BudgetMapper budgetMapper;
    private final MonthlyReportService reportService;

    private final Random random = new Random(20250101L);

    public DemoDataInitializer(AppProperties properties, UserService userService,
                               CategoryService categoryService, BillMapper billMapper,
                               BudgetMapper budgetMapper, MonthlyReportService reportService) {
        this.properties = properties;
        this.userService = userService;
        this.categoryService = categoryService;
        this.billMapper = billMapper;
        this.budgetMapper = budgetMapper;
        this.reportService = reportService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getDemo().isEnabled()) {
            return;
        }
        try {
            User demo = userService.createIfAbsent(properties.getDemo().getUsername(),
                    properties.getDemo().getPassword(), "记账小能手", UserRole.USER.name());
            User admin = userService.createIfAbsent(properties.getDemo().getAdminUsername(),
                    properties.getDemo().getAdminPassword(), "平台管理员", UserRole.ADMIN.name());
            log.info("演示账号：{} / {}（普通用户），{} / {}（管理员）",
                    demo.getUsername(), properties.getDemo().getPassword(),
                    admin.getUsername(), properties.getDemo().getAdminPassword());

            Long existing = billMapper.selectCount(new LambdaQueryWrapper<Bill>()
                    .eq(Bill::getUserId, demo.getId()));
            if (existing == null || existing == 0) {
                seedBills(demo);
                seedBudgets(demo);
                seedReports(demo);
                log.info("演示账单数据初始化完成");
            } else {
                log.info("演示账单已存在（{} 条），跳过初始化", existing);
            }
        } catch (Exception e) {
            log.warn("演示数据初始化失败（不影响服务启动）: {}", e.getMessage());
        }
    }

    /* ==================== 账单 ==================== */

    private void seedBills(User user) {
        Map<String, Category> categories = categoryService.listEntity(user.getId()).stream()
                .collect(Collectors.toMap(Category::getName, Function.identity(), (a, b) -> a));

        List<Bill> bills = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int days = Math.max(30, properties.getDemo().getBillDays());

        for (int i = days; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            DayOfWeek dow = date.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;

            // 固定支出：房租 / 水电 / 通讯
            if (date.getDayOfMonth() == 1) {
                add(bills, user, categories, "房租", amount(3200, 3600), date, "月度房租", null);
            }
            if (date.getDayOfMonth() == 5) {
                add(bills, user, categories, "水电燃气", amount(120, 320), date, "水电燃气缴费", "银行卡");
            }
            if (date.getDayOfMonth() == 6) {
                add(bills, user, categories, "宽带通讯", amount(80, 200), date, "话费+宽带", "支付宝");
            }
            // 固定收入：工资
            if (date.getDayOfMonth() == 10) {
                add(bills, user, categories, "基本工资", amount(16800, 19500), date, "月薪到账", "银行卡");
                if (random.nextDouble() < 0.5) {
                    add(bills, user, categories, "绩效奖金", amount(800, 3200), date, "季度绩效", "银行卡");
                }
            }
            if (random.nextDouble() < 0.06) {
                add(bills, user, categories, "兼职", amount(300, 1600), date, "副业收入", "支付宝");
            }

            // 餐饮
            if (random.nextDouble() < 0.75) {
                add(bills, user, categories, "早餐", amount(6, 18), date, pick("早餐", "包子豆浆", "煎饼果子"), pickPay());
            }
            if (random.nextDouble() < 0.85) {
                add(bills, user, categories, "午餐", amount(18, 42), date, pick("午餐", "公司附近简餐", "食堂"), pickPay());
            }
            if (random.nextDouble() < (weekend ? 0.9 : 0.7)) {
                add(bills, user, categories, "晚餐", amount(22, weekend ? 120 : 65), date,
                        pick("晚餐", "晚饭", "和朋友吃饭"), pickPay());
            }
            if (random.nextDouble() < 0.35) {
                add(bills, user, categories, "饮品零食", amount(9, 32), date,
                        pick("奶茶", "咖啡", "水果", "便利店零食"), pickPay());
            }
            if (random.nextDouble() < 0.12) {
                add(bills, user, categories, "外卖", amount(20, 55), date, "加班点外卖", pickPay());
            }

            // 交通
            if (!weekend && random.nextDouble() < 0.7) {
                add(bills, user, categories, "公交地铁", amount(4, 12), date, "通勤", "微信");
            }
            if (random.nextDouble() < 0.18) {
                add(bills, user, categories, "打车", amount(18, 68), date,
                        pick("打车回家", "加班打车", "赶时间打车"), pickPay());
            }
            if (random.nextDouble() < 0.04) {
                add(bills, user, categories, "火车高铁", amount(120, 620), date, "出行车票", "支付宝");
            }

            // 购物 / 娱乐
            if (random.nextDouble() < 0.13) {
                add(bills, user, categories, "日用百货", amount(30, 260), date, "超市采购", pickPay());
            }
            if (random.nextDouble() < 0.07) {
                add(bills, user, categories, "服饰鞋包", amount(120, 890), date,
                        pick("换季衣服", "买鞋", "网购"), pickPay());
            }
            if (random.nextDouble() < 0.03) {
                add(bills, user, categories, "数码电器", amount(180, 2400), date,
                        pick("数码配件", "耳机", "小家电"), "信用卡");
            }
            if (weekend && random.nextDouble() < 0.3) {
                add(bills, user, categories, pick("电影演出", "游戏"), date,
                        pick("电影", "剧本杀", "展览", "游戏充值"));
            }
            if (random.nextDouble() < 0.05) {
                add(bills, user, categories, "运动健身", amount(60, 400), date, "健身房/球场", pickPay());
            }
            // 医疗 / 学习 / 人情
            if (random.nextDouble() < 0.02) {
                add(bills, user, categories, "药品", amount(15, 180), date, "买药", "微信");
            }
            if (random.nextDouble() < 0.03) {
                add(bills, user, categories, "书籍", amount(30, 160), date, "买书", pickPay());
            }
            if (random.nextDouble() < 0.04) {
                add(bills, user, categories, "红包", amount(50, 600), date, "发红包", "微信");
            }
        }
        // 让部分账单带上 AI 记账的痕迹
        bills.stream()
                .filter(bill -> random.nextDouble() < 0.12)
                .forEach(bill -> {
                    bill.setSource(BillSource.AI.name());
                    bill.setAiInput(bill.getRemark() + " " + bill.getAmount().stripTrailingZeros().toPlainString() + " 元");
                });

        bills.forEach(billMapper::insert);
        log.info("已生成演示账单 {} 条", bills.size());
    }

    private void add(List<Bill> bills, User user, Map<String, Category> categories,
                     String categoryName, BigDecimal amount, LocalDate date, String remark, String payMethod) {
        Category category = categories.get(categoryName);
        if (category == null) {
            return;
        }
        Bill bill = newBill(user, category, amount, date, remark, payMethod);
        bills.add(bill);
    }

    /** 娱乐类金额由内部随机，避免调用方重复写 */
    private void add(List<Bill> bills, User user, Map<String, Category> categories,
                     String categoryName, LocalDate date, String remark) {
        add(bills, user, categories, categoryName, amount(50, 320), date, remark, pickPay());
    }

    private Bill newBill(User user, Category category, BigDecimal amount, LocalDate date,
                         String remark, String payMethod) {
        Bill bill = new Bill();
        bill.setUserId(user.getId());
        bill.setType(category.getType());
        bill.setCategoryId(category.getId());
        bill.setParentCategoryId(category.getParentId() == null || category.getParentId() == 0
                ? category.getId() : category.getParentId());
        bill.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        bill.setRemark(remark);
        bill.setBillDate(date);
        bill.setBillTime(date.atTime(8 + random.nextInt(13), random.nextInt(60)));
        bill.setPayMethod(payMethod);
        bill.setSource(BillSource.MANUAL.name());
        return bill;
    }

    /* ==================== 预算 ==================== */

    private void seedBudgets(User user) {
        String month = DateUtils.currentMonth();
        Map<String, Category> categories = categoryService.listEntity(user.getId()).stream()
                .collect(Collectors.toMap(Category::getName, Function.identity(), (a, b) -> a));
        Map<Long, BigDecimal> plans = new HashMap<>();
        plans.put(0L, new BigDecimal("7000"));
        addBudgetPlan(plans, categories, "餐饮", new BigDecimal("2600"));
        addBudgetPlan(plans, categories, "交通", new BigDecimal("600"));
        addBudgetPlan(plans, categories, "购物", new BigDecimal("1500"));
        addBudgetPlan(plans, categories, "娱乐", new BigDecimal("800"));
        addBudgetPlan(plans, categories, "居住", new BigDecimal("3800"));

        plans.forEach((categoryId, amount) -> {
            Budget budget = new Budget();
            budget.setUserId(user.getId());
            budget.setYearMonth(month);
            budget.setCategoryId(categoryId);
            budget.setAmount(amount);
            budget.setAlertThreshold(80);
            budgetMapper.insert(budget);
        });
        log.info("已生成 {} 月预算 {} 条", month, plans.size());
    }

    private void addBudgetPlan(Map<Long, BigDecimal> plans, Map<String, Category> categories,
                               String categoryName, BigDecimal amount) {
        Category category = categories.get(categoryName);
        if (category != null) {
            plans.put(category.getId(), amount);
        }
    }

    /* ==================== 报告 ==================== */

    private void seedReports(User user) {
        YearMonth now = YearMonth.now();
        for (int i = 1; i <= 2; i++) {
            String month = now.minusMonths(i).format(DateUtils.YM);
            try {
                reportService.generate(user.getId(), month);
            } catch (Exception e) {
                log.debug("{} 月报告生成跳过: {}", month, e.getMessage());
            }
        }
    }

    /* ==================== 工具 ==================== */

    private BigDecimal amount(double min, double max) {
        double value = min + random.nextDouble() * (max - min);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String pick(String... options) {
        return options[random.nextInt(options.length)];
    }

    private String pickPay() {
        return PAY_METHODS.get(random.nextInt(PAY_METHODS.size()));
    }

    /** 未使用：保留给未来的账单日期展示 */
    private String label(LocalDate date) {
        return date.format(MD);
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private String billType(BillType type) {
        return type.name();
    }
}
