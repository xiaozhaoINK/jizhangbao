package com.jizhangbao.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 日期与金额工具。
 */
public final class DateUtils {

    public static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    public static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter YMD_CN = DateTimeFormatter.ofPattern("yyyy年M月d日");

    private DateUtils() {
    }

    public static String currentMonth() {
        return YearMonth.now().format(YM);
    }

    public static String toMonth(LocalDate date) {
        return YearMonth.from(date).format(YM);
    }

    public static YearMonth parseMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) {
            return YearMonth.now();
        }
        return YearMonth.parse(yearMonth.trim(), YM);
    }

    public static LocalDate monthStart(String yearMonth) {
        return parseMonth(yearMonth).atDay(1);
    }

    public static LocalDate monthEnd(String yearMonth) {
        return parseMonth(yearMonth).atEndOfMonth();
    }

    /** 当月第一天 00:00:00 */
    public static LocalDateTime monthStartTime(String yearMonth) {
        return monthStart(yearMonth).atStartOfDay();
    }

    /** 当月最后一天 23:59:59 */
    public static LocalDateTime monthEndTime(String yearMonth) {
        return monthEnd(yearMonth).atTime(23, 59, 59);
    }

    public static int daysInMonth(String yearMonth) {
        return parseMonth(yearMonth).lengthOfMonth();
    }

    /** 生成 [start, end] 之间（按天）的日期列表 */
    public static List<LocalDate> datesBetween(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    /** 生成 [start, end] 之间（按月）的月份列表，格式 yyyy-MM */
    public static List<String> monthsBetween(String startMonth, String endMonth) {
        YearMonth start = parseMonth(startMonth);
        YearMonth end = parseMonth(endMonth);
        List<String> months = new ArrayList<>();
        YearMonth cursor = start;
        while (!cursor.isAfter(end)) {
            months.add(cursor.format(YM));
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    /** 相对当前月份偏移，offset 为负表示往前 */
    public static String monthOffset(int offset) {
        return YearMonth.now().plusMonths(offset).format(YM);
    }

    /** 上月 */
    public static String lastMonth() {
        return monthOffset(-1);
    }

    public static String humanDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "今天";
        }
        if (date.equals(today.minusDays(1))) {
            return "昨天";
        }
        if (date.equals(today.minusDays(2))) {
            return "前天";
        }
        return date.format(YMD_CN);
    }

    public static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return dividend.divide(divisor, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
