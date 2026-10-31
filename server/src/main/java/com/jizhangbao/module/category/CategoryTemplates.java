package com.jizhangbao.module.category;

import java.util.List;

/**
 * 新用户注册时自动创建的内置分类模板。
 *
 * <p>采用「一级分类 + 二级分类」结构，覆盖日常记账场景，
 * 也让 AI 记账的意图识别有稳定的目标分类。</p>
 */
public final class CategoryTemplates {

    private CategoryTemplates() {
    }

    /**
     * 一级分类定义：名称 / 图标 / 颜色 / 二级分类（逗号分隔）
     */
    public record Group(String name, String icon, String color, List<String> children) {
    }

    public static final List<Group> EXPENSE = List.of(
            new Group("餐饮", "food", "#FF7A45", List.of("早餐", "午餐", "晚餐", "外卖", "饮品零食", "夜宵")),
            new Group("交通", "transport", "#40A9FF", List.of("公交地铁", "打车", "加油", "停车费", "火车高铁")),
            new Group("购物", "shopping", "#9254DE", List.of("服饰鞋包", "数码电器", "日用百货", "美妆护肤")),
            new Group("居住", "home", "#36CFC9", List.of("房租", "水电燃气", "物业费", "宽带通讯")),
            new Group("娱乐", "entertainment", "#F759AB", List.of("电影演出", "游戏", "旅游", "运动健身")),
            new Group("医疗健康", "medical", "#73D13D", List.of("门诊", "药品", "体检")),
            new Group("教育学习", "education", "#597EF7", List.of("课程培训", "书籍", "考试报名")),
            new Group("人情往来", "gift", "#FFC53D", List.of("红包", "礼物", "请客")),
            new Group("其他支出", "other", "#8C8C8C", List.of("其他"))
    );

    public static final List<Group> INCOME = List.of(
            new Group("工资薪酬", "salary", "#52C41A", List.of("基本工资", "绩效奖金", "加班补贴")),
            new Group("兼职副业", "parttime", "#13C2C2", List.of("兼职", "稿费", "外包")),
            new Group("投资理财", "invest", "#FAAD14", List.of("基金收益", "股票收益", "利息")),
            new Group("红包礼金", "redpacket", "#FF7A45", List.of("红包", "彩礼")),
            new Group("报销退款", "refund", "#2F54EB", List.of("报销", "退款")),
            new Group("其他收入", "other-income", "#8C8C8C", List.of("其他"))
    );
}
