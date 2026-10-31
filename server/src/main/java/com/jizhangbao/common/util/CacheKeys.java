package com.jizhangbao.common.util;

/**
 * Redis Key 统一管理，避免散落在业务代码里。
 *
 * <p>命名规范：{@code jzb:{模块}:{业务}:{维度}}</p>
 */
public final class CacheKeys {

    private CacheKeys() {
    }

    /** 登出后的 token 黑名单，TTL 与 token 剩余有效期一致 */
    public static String tokenBlacklist(String token) {
        return "jzb:auth:blacklist:" + token;
    }

    /** 用户信息缓存 */
    public static String userProfile(Long userId) {
        return "jzb:user:profile:" + userId;
    }

    /** 首页概览统计缓存（短 TTL，写账单时主动失效） */
    public static String statsOverview(Long userId, String dimension) {
        return "jzb:stats:overview:" + userId + ":" + dimension;
    }

    /** AI 记账解析结果缓存，命中后不再消耗大模型 token */
    public static String aiParse(Long userId, String textHash) {
        return "jzb:ai:parse:" + userId + ":" + textHash;
    }

    /** AI 接口按分钟限流 */
    public static String aiRateLimit(Long userId, String minute) {
        return "jzb:rate:ai:" + userId + ":" + minute;
    }

    /** 用户当月预算提醒去重 */
    public static String budgetAlert(Long userId, String yearMonth, Long categoryId) {
        return "jzb:budget:alert:" + userId + ":" + yearMonth + ":" + categoryId;
    }

    /** 月度报告生成锁，避免并发重复生成 */
    public static String reportLock(Long userId, String yearMonth) {
        return "jzb:report:lock:" + userId + ":" + yearMonth;
    }
}
