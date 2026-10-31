package com.jizhangbao.module.category.support;

import com.jizhangbao.module.category.entity.Category;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类关键词匹配器。
 *
 * <p>规则引擎与「AI 记账降级」共用同一套关键词表：先把口语关键词映射到二级分类名，
 * 再回到用户自己的分类列表里找对应实体，因此用户自定义分类也能命中。</p>
 */
public final class CategoryMatcher {

    private CategoryMatcher() {
    }

    /** 二级分类名 -> 常见口语关键词（命中越长优先级越高） */
    private static final Map<String, String[]> KEYWORD_RULES = new LinkedHashMap<>();

    /** 一级分类默认兜底关键词 */
    private static final Map<String, String> PARENT_FALLBACK = Map.of(
            "餐饮", "吃",
            "交通", "车",
            "购物", "买",
            "居住", "住",
            "娱乐", "玩",
            "医疗健康", "医",
            "教育学习", "学",
            "人情往来", "送"
    );

    static {
        // ---------------- 支出 ----------------
        rule("早餐", "早餐", "早饭", "包子", "豆浆", "油条", "煎饼", "馄饨");
        rule("午餐", "午餐", "午饭", "中饭", "工作餐", "快餐", "食堂");
        rule("晚餐", "晚餐", "晚饭", "夜宵", "宵夜", "烧烤", "火锅", "炒菜");
        rule("外卖", "外卖", "美团", "饿了么", "点餐", "配送", "跑腿");
        rule("饮品零食", "奶茶", "咖啡", "饮料", "可乐", "零食", "水果", "雪糕", "蛋糕", "星巴克", "瑞幸");
        rule("公交地铁", "地铁", "公交", "轻轨", "交通卡", "单车", "巴士");
        rule("打车", "打车", "滴滴", "出租车", "网约车", "高德打车", "曹操");
        rule("加油", "加油", "汽油", "充电桩", "油费", "洗车");
        rule("停车费", "停车", "停车费");
        rule("火车高铁", "火车", "高铁", "动车", "机票", "航班", "卧铺");
        rule("服饰鞋包", "衣服", "裤子", "鞋", "外套", "包包", "服饰", "裙子", "帽");
        rule("数码电器", "手机", "电脑", "耳机", "相机", "键盘", "显示器", "充电器", "数码");
        rule("日用百货", "超市", "日用", "纸巾", "洗发水", "牙膏", "洗衣液", "超市购");
        rule("美妆护肤", "化妆品", "护肤", "口红", "面膜", "洗面奶", "美妆");
        rule("房租", "房租", "租金", "租房", "押金");
        rule("水电燃气", "水费", "电费", "燃气", "暖气", "水电");
        rule("物业费", "物业", "物业费");
        rule("宽带通讯", "宽带", "话费", "流量", "手机费", "网费");
        rule("电影演出", "电影", "演出", "话剧", "演唱会", "看剧");
        rule("游戏", "游戏", "充值", "点卡", "皮肤", "steam");
        rule("旅游", "旅游", "酒店", "门票", "景点", "民宿", "度假");
        rule("运动健身", "健身房", "健身", "瑜伽", "游泳", "球馆", "跑步");
        rule("门诊", "看病", "挂号", "门诊", "医院", "就诊");
        rule("药品", "买药", "药店", "药品");
        rule("体检", "体检");
        rule("课程培训", "课程", "培训", "网课", "学费", "训练营");
        rule("书籍", "图书", "教材", "买书");
        rule("考试报名", "报名", "考试", "证书", "考证");
        rule("红包", "发红包", "红包");
        rule("礼物", "礼物", "礼品", "送礼");
        rule("请客", "请客", "聚餐", "团建", "AA");
        rule("其他", "其他");

        // ---------------- 收入 ----------------
        rule("基本工资", "工资", "薪水", "月薪", "发薪", "发的工资");
        rule("绩效奖金", "奖金", "绩效", "年终奖", "提成", "红包奖励");
        rule("加班补贴", "加班费", "补贴", "津贴");
        rule("兼职", "兼职", "外快", "私活");
        rule("稿费", "稿费", "稿酬", "约稿");
        rule("外包", "外包", "接单", "项目款");
        rule("基金收益", "基金", "理财收益", "收益");
        rule("股票收益", "股票", "炒股", "分红");
        rule("利息", "利息", "存款利息");
        rule("红包", "收红包", "红包");
        rule("报销", "报销", "报账");
        rule("退款", "退款", "退货", "返现");
        rule("其他", "其他");
    }

    private static void rule(String categoryName, String... keywords) {
        KEYWORD_RULES.put(categoryName, keywords);
    }

    /**
     * 在候选分类中匹配最可能的分类。
     *
     * @param candidates 该用户指定类型（收入/支出）下的全部分类
     * @param text       自然语言文本
     * @return 命中分类（优先二级分类），未命中返回 null
     */
    public static Category match(List<Category> candidates, String text) {
        if (!StringUtils.hasText(text) || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase();

        Map<String, Category> byName = new HashMap<>();
        for (Category category : candidates) {
            byName.putIfAbsent(category.getName(), category);
        }

        String bestName = null;
        int bestScore = 0;
        for (Map.Entry<String, String[]> entry : KEYWORD_RULES.entrySet()) {
            if (!byName.containsKey(entry.getKey())) {
                continue;
            }
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword.toLowerCase()) && keyword.length() > bestScore) {
                    bestScore = keyword.length();
                    bestName = entry.getKey();
                }
            }
        }

        // 直接命中分类名（用户自定义分类最容易走这里）
        for (Category category : candidates) {
            if (lower.contains(category.getName().toLowerCase())
                    && category.getName().length() > bestScore) {
                bestScore = category.getName().length();
                bestName = category.getName();
            }
        }
        if (bestName != null) {
            return byName.get(bestName);
        }

        // 一级分类兜底
        for (Map.Entry<String, String> entry : PARENT_FALLBACK.entrySet()) {
            if (lower.contains(entry.getValue())) {
                for (Category category : candidates) {
                    if (category.getName().equals(entry.getKey())) {
                        return category;
                    }
                }
            }
        }
        return null;
    }

    /** 按名称精确查找 */
    public static Category matchByName(List<Category> candidates, String name) {
        if (!StringUtils.hasText(name) || candidates == null) {
            return null;
        }
        String normalized = name.trim();
        for (Category category : candidates) {
            if (category.getName().equals(normalized)) {
                return category;
            }
        }
        for (Category category : candidates) {
            if (category.getName().contains(normalized) || normalized.contains(category.getName())) {
                return category;
            }
        }
        return null;
    }

    /** 所有已知的二级分类名，供大模型提示词使用 */
    public static List<String> knownCategoryNames() {
        return List.copyOf(KEYWORD_RULES.keySet());
    }
}
