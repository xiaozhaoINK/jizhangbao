package com.jizhangbao.common.util;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 中文数字 -> 阿拉伯数字。
 *
 * <p>用于自然语言记账：「午饭三十块」「打车十五块五」这类口语表达。</p>
 */
public final class ChineseNumberUtils {

    private static final Map<Character, Integer> DIGITS = new HashMap<>();
    private static final Map<Character, Integer> UNITS = new HashMap<>();

    static {
        DIGITS.put('零', 0);
        DIGITS.put('〇', 0);
        DIGITS.put('一', 1);
        DIGITS.put('壹', 1);
        DIGITS.put('二', 2);
        DIGITS.put('贰', 2);
        DIGITS.put('两', 2);
        DIGITS.put('三', 3);
        DIGITS.put('叁', 3);
        DIGITS.put('四', 4);
        DIGITS.put('肆', 4);
        DIGITS.put('五', 5);
        DIGITS.put('伍', 5);
        DIGITS.put('六', 6);
        DIGITS.put('陆', 6);
        DIGITS.put('七', 7);
        DIGITS.put('柒', 7);
        DIGITS.put('八', 8);
        DIGITS.put('捌', 8);
        DIGITS.put('九', 9);
        DIGITS.put('玖', 9);

        UNITS.put('十', 10);
        UNITS.put('拾', 10);
        UNITS.put('百', 100);
        UNITS.put('佰', 100);
        UNITS.put('千', 1000);
        UNITS.put('仟', 1000);
    }

    private ChineseNumberUtils() {
    }

    /**
     * 判断字符串是否完全由中文数字字符构成（可包含 十百千）。
     */
    public static boolean isChineseNumber(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (char c : text.toCharArray()) {
            if (!DIGITS.containsKey(c) && !UNITS.containsKey(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析不含「万/亿」的常见中文数字，失败返回 null。
     *
     * <p>示例：三十 -> 30，十五 -> 15，一百二十 -> 120，两千 -> 2000，五 -> 5</p>
     */
    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank() || !isChineseNumber(text)) {
            return null;
        }
        if (text.length() == 1 && DIGITS.containsKey(text.charAt(0))) {
            return BigDecimal.valueOf(DIGITS.get(text.charAt(0)));
        }
        int total = 0;
        int section = 0;
        Integer pendingDigit = null;
        for (char c : text.toCharArray()) {
            Integer digit = DIGITS.get(c);
            if (digit != null) {
                pendingDigit = digit;
                continue;
            }
            Integer unit = UNITS.get(c);
            if (unit == null) {
                return null;
            }
            int multiplier = pendingDigit == null ? 1 : pendingDigit;
            // 「十五」中十前面没有数字，按 1 处理
            section += multiplier * unit;
            pendingDigit = null;
        }
        if (pendingDigit != null) {
            section += pendingDigit;
        }
        total += section;
        return total == 0 ? null : BigDecimal.valueOf(total);
    }
}
