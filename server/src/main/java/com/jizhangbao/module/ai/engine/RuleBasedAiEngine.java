package com.jizhangbao.module.ai.engine;

import com.jizhangbao.common.enums.BillType;
import com.jizhangbao.common.util.ChineseNumberUtils;
import com.jizhangbao.common.util.DateUtils;
import com.jizhangbao.module.category.entity.Category;
import com.jizhangbao.module.category.support.CategoryMatcher;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地规则引擎：不依赖任何外部服务，用正则 + 关键词完成自然语言记账解析。
 *
 * <p>两条价值：</p>
 * <ol>
 *   <li>离线 / 无 API Key 时项目依旧可完整演示；</li>
 *   <li>大模型调用失败时自动降级，保证记账入口永远可用。</li>
 * </ol>
 */
public class RuleBasedAiEngine implements AiEngine {

    /* 金额：优先「数字 + 货币单位」，其次中文数字，最后裸数字 */
    private static final Pattern AMOUNT_WITH_UNIT = Pattern.compile(
            "(\\d+(?:\\.\\d{1,2})?)\\s*(?:元|块钱|块|圆|rmb|RMB|¥|￥)");
    private static final Pattern CHINESE_AMOUNT = Pattern.compile(
            "([零〇一壹两二贰三叁四肆五伍六陆七柒八捌九玖十拾百佰千仟]+)\\s*(?:元|块钱|块|圆)");
    private static final Pattern BARE_NUMBER = Pattern.compile("(\\d+(?:\\.\\d{1,2})?)");

    /* 日期 */
    private static final Pattern FULL_DATE = Pattern.compile(
            "(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})[日号]?");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]");
    private static final Pattern DAY_ONLY = Pattern.compile("(\\d{1,2})\\s*[日号]");
    private static final Pattern WEEKDAY = Pattern.compile("(上|这|本)?(?:周|星期|礼拜)([一二三四五六日天1-7])");

    /* 收入信号词 */
    private static final List<String> INCOME_SIGNALS = List.of(
            "收入", "工资", "发工资", "发了", "发薪", "奖金", "年终奖", "提成", "报销", "退款", "退货",
            "收到", "进账", "到账", "赚了", "赚到", "收益", "利息", "分红", "兼职", "稿费", "外快",
            "收红包", "领到", "返现", "补贴", "津贴", "项目款");

    /* 支付方式 */
    private static final List<String> PAY_METHODS = List.of(
            "微信", "支付宝", "银行卡", "信用卡", "借记卡", "花呗", "现金", "云闪付", "Apple Pay");

    private static final List<String> NOISE_WORDS = List.of(
            "花了", "花掉", "消费了", "支出", "付了", "用了", "买的", "买了", "今天", "昨天", "前天",
            "大前天", "上午", "下午", "中午", "晚上", "早上", "晚饭", "一共", "总共", "大概", "差不多",
            "记账", "记一笔", "记得");

    @Override
    public String name() {
        return "RULE";
    }

    @Override
    public boolean llm() {
        return false;
    }

    @Override
    public ParsedBill parseBill(ParseInput input) {
        String raw = input.text() == null ? "" : input.text().trim();
        String text = raw.replace(" ", "");

        BigDecimal amount = extractAmount(text);
        String type = detectType(text);
        LocalDate billDate = extractDate(text);
        String payMethod = extractPayMethod(text);

        List<Category> candidates = BillType.INCOME.name().equals(type)
                ? input.incomeCategories() : input.expenseCategories();
        Category category = CategoryMatcher.match(candidates, text);

        String remark = buildRemark(raw, category);
        double confidence = 0.35;
        if (amount != null) {
            confidence += 0.35;
        }
        if (category != null) {
            confidence += 0.2;
        }
        if (billDate != null) {
            confidence += 0.1;
        }
        confidence = Math.min(0.95, confidence);

        return new ParsedBill(
                type,
                amount,
                category == null ? null : category.getName(),
                billDate == null ? LocalDate.now() : billDate,
                remark,
                payMethod,
                Math.round(confidence * 100) / 100.0
        );
    }

    /* ==================== 抽取逻辑 ==================== */

    private BigDecimal extractAmount(String text) {
        Matcher withUnit = AMOUNT_WITH_UNIT.matcher(text);
        if (withUnit.find()) {
            return new BigDecimal(withUnit.group(1));
        }
        Matcher chinese = CHINESE_AMOUNT.matcher(text);
        if (chinese.find()) {
            BigDecimal value = ChineseNumberUtils.parse(chinese.group(1));
            if (value != null) {
                return value;
            }
        }
        // 裸数字：排除日期形态（如 2025-06-15、6月15日、15号）
        String cleaned = FULL_DATE.matcher(text).replaceAll(" ");
        cleaned = MONTH_DAY.matcher(cleaned).replaceAll(" ");
        cleaned = DAY_ONLY.matcher(cleaned).replaceAll(" ");
        Matcher bare = BARE_NUMBER.matcher(cleaned);
        while (bare.find()) {
            String candidate = bare.group(1);
            if (candidate.length() == 4 && candidate.startsWith("20")) {
                continue;
            }
            try {
                BigDecimal value = new BigDecimal(candidate);
                if (value.compareTo(BigDecimal.ZERO) > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // 忽略
            }
        }
        return null;
    }

    private String detectType(String text) {
        for (String signal : INCOME_SIGNALS) {
            if (text.contains(signal)) {
                return BillType.INCOME.name();
            }
        }
        return BillType.EXPENSE.name();
    }

    private LocalDate extractDate(String text) {
        LocalDate today = LocalDate.now();
        Matcher full = FULL_DATE.matcher(text);
        if (full.find()) {
            try {
                return LocalDate.of(Integer.parseInt(full.group(1)),
                        Integer.parseInt(full.group(2)), Integer.parseInt(full.group(3)));
            } catch (Exception ignored) {
                // 继续尝试其它格式
            }
        }
        Matcher monthDay = MONTH_DAY.matcher(text);
        if (monthDay.find()) {
            try {
                int month = Integer.parseInt(monthDay.group(1));
                int day = Integer.parseInt(monthDay.group(2));
                LocalDate date = LocalDate.of(today.getYear(), month, day);
                return date.isAfter(today) ? date.minusYears(1) : date;
            } catch (Exception ignored) {
                // 继续
            }
        }
        if (text.contains("大前天")) {
            return today.minusDays(3);
        }
        if (text.contains("前天")) {
            return today.minusDays(2);
        }
        if (text.contains("昨天") || text.contains("昨晚") || text.contains("昨日")) {
            return today.minusDays(1);
        }
        if (text.contains("明天")) {
            return today.plusDays(1);
        }
        Matcher weekday = WEEKDAY.matcher(text);
        if (weekday.find()) {
            int target = weekdayIndex(weekday.group(2));
            if (target > 0) {
                int current = today.getDayOfWeek().getValue();
                int diff = current - target;
                if (diff < 0) {
                    diff += 7;
                }
                LocalDate date = today.minusDays(diff);
                String prefix = weekday.group(1);
                if ("上".equals(prefix) && diff == 0) {
                    date = date.minusWeeks(1);
                }
                return date;
            }
        }
        Matcher dayOnly = DAY_ONLY.matcher(text);
        if (dayOnly.find()) {
            try {
                int day = Integer.parseInt(dayOnly.group(1));
                if (day >= 1 && day <= 31) {
                    LocalDate candidate = today.withDayOfMonth(Math.min(day, today.lengthOfMonth()));
                    return candidate.isAfter(today) ? candidate.minusMonths(1) : candidate;
                }
            } catch (Exception ignored) {
                // 忽略
            }
        }
        if (text.contains("今天") || text.contains("今日") || text.contains("今晚")) {
            return today;
        }
        return null;
    }

    private int weekdayIndex(String token) {
        if (!StringUtils.hasText(token)) {
            return 0;
        }
        int value = switch (token) {
            case "一", "1" -> DayOfWeek.MONDAY.getValue();
            case "二", "2" -> DayOfWeek.TUESDAY.getValue();
            case "三", "3" -> DayOfWeek.WEDNESDAY.getValue();
            case "四", "4" -> DayOfWeek.THURSDAY.getValue();
            case "五", "5" -> DayOfWeek.FRIDAY.getValue();
            case "六", "6" -> DayOfWeek.SATURDAY.getValue();
            case "日", "天", "7" -> DayOfWeek.SUNDAY.getValue();
            default -> 0;
        };
        return value;
    }

    private String extractPayMethod(String text) {
        for (String method : PAY_METHODS) {
            if (text.contains(method)) {
                return "Apple Pay".equals(method) ? "Apple Pay" : method;
            }
        }
        return null;
    }

    /**
     * 备注：去掉金额、日期、口语噪音后剩下的内容；为空则用分类名。
     */
    private String buildRemark(String raw, Category category) {
        if (!StringUtils.hasText(raw)) {
            return category == null ? "AI 记账" : category.getName();
        }
        String remark = raw;
        remark = AMOUNT_WITH_UNIT.matcher(remark).replaceAll(" ");
        remark = CHINESE_AMOUNT.matcher(remark).replaceAll(" ");
        remark = FULL_DATE.matcher(remark).replaceAll(" ");
        remark = MONTH_DAY.matcher(remark).replaceAll(" ");
        remark = DAY_ONLY.matcher(remark).replaceAll(" ");
        for (String noise : NOISE_WORDS) {
            remark = remark.replace(noise, " ");
        }
        for (String method : PAY_METHODS) {
            remark = remark.replace(method, " ");
        }
        // 清掉残留的裸数字（「30」「28.5」）与无意义的语气词
        remark = remark.replaceAll("\\d+(?:\\.\\d{1,2})?", " ");
        remark = remark.replace("付的", " ").replace("付款", " ").replace("一共", " ");
        remark = remark.replaceAll("[，,。.、!！?？;；:：()（）\\[\\]\"']", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // 清掉孤立的口语助词（「用微信付的」剥掉支付方式后剩下的「用」）
        remark = remark.replaceAll("(?<=^|\\s)[用了的和在把给](?=\\s|$)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (remark.length() >= 2) {
            return remark.length() > 100 ? remark.substring(0, 100) : remark;
        }
        return category == null ? "AI 记账" : category.getName();
    }

    /* ==================== 问答 / 补全 ==================== */

    @Override
    public String chat(String systemPrompt, List<ChatTurn> history, String question,
                       java.util.function.Consumer<String> onChunk) {
        // 规则引擎模式下问答由 LocalAnswerGenerator 负责（在 Service 层），这里不会走到
        String answer = "当前运行在本地规则模式，未接入大模型。";
        onChunk.accept(answer);
        return answer;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return "";
    }

    /** 供上层做「关键词是否命中」的调试能力 */
    public List<String> matchedKeywords(String text) {
        List<String> hits = new ArrayList<>();
        if (extractAmount(text) != null) {
            hits.add("金额");
        }
        if (extractDate(text) != null) {
            hits.add("日期");
        }
        if (extractPayMethod(text) != null) {
            hits.add("支付方式");
        }
        return hits;
    }

    /** 展示用：今天日期的中文写法 */
    public String todayLabel() {
        return DateUtils.humanDate(LocalDate.now());
    }
}
