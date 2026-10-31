package com.jizhangbao.common.exception;

/**
 * 统一业务错误码。
 *
 * <p>约定：1xxx 通用 / 2xxx 用户认证 / 3xxx 账单与分类 / 4xxx 预算与报告 / 5xxx AI 能力</p>
 */
public enum ErrorCode {

    SUCCESS(0, "success"),

    /* ---------- 通用 ---------- */
    BAD_REQUEST(1001, "请求参数不合法"),
    UNAUTHORIZED(1002, "请先登录"),
    FORBIDDEN(1003, "没有权限执行该操作"),
    NOT_FOUND(1004, "请求的资源不存在"),
    METHOD_NOT_ALLOWED(1005, "请求方式不支持"),
    INTERNAL_ERROR(1500, "服务器开小差了，请稍后再试"),
    TOO_MANY_REQUESTS(1429, "操作过于频繁，请稍后再试"),

    /* ---------- 用户 ---------- */
    USERNAME_EXISTS(2001, "该账号已被注册"),
    USER_NOT_FOUND(2002, "用户不存在"),
    PASSWORD_ERROR(2003, "账号或密码错误"),
    USER_DISABLED(2004, "账号已被禁用，请联系管理员"),
    OLD_PASSWORD_ERROR(2005, "原密码不正确"),
    TOKEN_EXPIRED(2006, "登录已过期，请重新登录"),
    TOKEN_INVALID(2007, "登录凭证无效"),

    /* ---------- 账单与分类 ---------- */
    BILL_NOT_FOUND(3001, "账单不存在"),
    BILL_NOT_OWNED(3002, "无权操作他人的账单"),
    CATEGORY_NOT_FOUND(3003, "分类不存在"),
    CATEGORY_HAS_CHILDREN(3004, "请先删除下级分类"),
    CATEGORY_HAS_BILLS(3005, "该分类下还有账单，无法删除"),
    CATEGORY_SYSTEM_PROTECTED(3006, "系统内置分类不可删除"),
    CATEGORY_TYPE_MISMATCH(3007, "分类类型与账单类型不一致"),

    /* ---------- 预算与报告 ---------- */
    BUDGET_NOT_FOUND(4001, "预算不存在"),
    BUDGET_ALREADY_EXISTS(4002, "该月份已存在同类预算"),
    REPORT_NOT_FOUND(4003, "报告不存在"),
    REPORT_EMPTY_DATA(4004, "当月没有账单数据，暂时无法生成报告"),

    /* ---------- AI ---------- */
    AI_PARSE_FAILED(5001, "没能理解这句话，换个说法试试？"),
    AI_SERVICE_ERROR(5002, "AI 服务暂时不可用，请稍后再试"),
    AI_NOT_ENABLED(5003, "AI 能力未启用"),
    AI_AMOUNT_MISSING(5004, "没有识别到金额，请补充金额信息");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
