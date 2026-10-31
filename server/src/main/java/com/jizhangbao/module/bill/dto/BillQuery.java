package com.jizhangbao.module.bill.dto;

import java.time.LocalDate;

/**
 * 账单查询条件。
 */
public class BillQuery {

    private Integer page = 1;
    private Integer size = 10;

    private LocalDate startDate;
    private LocalDate endDate;

    /** INCOME / EXPENSE，空表示全部 */
    private String type;

    private Long categoryId;

    /** 关键字，匹配备注 / 标签 / AI 原始输入 */
    private String keyword;

    private java.math.BigDecimal minAmount;
    private java.math.BigDecimal maxAmount;

    private String payMethod;

    /** billDate=按日期倒序（默认），amount=按金额倒序，createdAt=按创建时间倒序 */
    private String sortBy = "billDate";

    private String sortOrder = "desc";

    public Integer getPage() {
        return page == null || page < 1 ? 1 : page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size == null || size < 1 ? 10 : Math.min(size, 200);
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public java.math.BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(java.math.BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public java.math.BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(java.math.BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public String getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(String payMethod) {
        this.payMethod = payMethod;
    }

    public String getSortBy() {
        return sortBy == null ? "billDate" : sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortOrder() {
        return sortOrder == null ? "desc" : sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }
}
