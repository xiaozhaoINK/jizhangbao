package com.jizhangbao.module.bill.service;

import com.jizhangbao.common.api.PageResult;
import com.jizhangbao.module.bill.dto.BillQuery;
import com.jizhangbao.module.bill.dto.BillRequest;
import com.jizhangbao.module.bill.dto.BillSummaryVO;
import com.jizhangbao.module.bill.dto.BillVO;
import com.jizhangbao.module.bill.entity.Bill;

import java.time.LocalDate;
import java.util.List;

/**
 * 账单服务。
 */
public interface BillService {

    PageResult<BillVO> page(Long userId, BillQuery query);

    BillVO detail(Long userId, Long id);

    BillVO create(Long userId, BillRequest request);

    BillVO update(Long userId, Long id, BillRequest request);

    void delete(Long userId, Long id);

    BillSummaryVO summary(Long userId, LocalDate start, LocalDate end);

    List<BillVO> recent(Long userId, int limit);

    /** AI 记账落库入口 */
    Bill createFromAi(Long userId, Long categoryId, String type, java.math.BigDecimal amount,
                      LocalDate billDate, String remark, String payMethod, String rawInput);

    BillVO toVO(Bill bill, java.util.Map<Long, com.jizhangbao.module.category.entity.Category> categoryMap);

    /** 批量转换（内部复用分类映射，避免 N+1 查询） */
    List<BillVO> toVOList(List<Bill> bills, Long userId);

    long countByUser(Long userId);
}
