package com.jizhangbao.module.bill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhangbao.module.bill.entity.Bill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 账单 Mapper，复杂统计使用自定义 SQL（见 resources/mapper/BillMapper.xml）。
 */
@Mapper
public interface BillMapper extends BaseMapper<Bill> {

    /**
     * 按天聚合收支，用于趋势图。
     */
    List<Map<String, Object>> sumByDay(@Param("userId") Long userId,
                                       @Param("start") LocalDate start,
                                       @Param("end") LocalDate end,
                                       @Param("type") String type);

    /**
     * 按月聚合收支，用于月度对比。
     */
    List<Map<String, Object>> sumByMonth(@Param("userId") Long userId,
                                         @Param("startMonth") String startMonth,
                                         @Param("endMonth") String endMonth);

    /**
     * 按分类聚合，用于分类占比。
     */
    List<Map<String, Object>> sumByCategory(@Param("userId") Long userId,
                                            @Param("start") LocalDate start,
                                            @Param("end") LocalDate end,
                                            @Param("type") String type,
                                            @Param("level") String level);

    /**
     * 某时间区间内的合计。
     */
    Map<String, Object> sumRange(@Param("userId") Long userId,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end);

    /**
     * 最近有账单的日期（用于计算连续记账天数）。
     */
    @Select("SELECT DISTINCT bill_date FROM t_bill WHERE user_id = #{userId} AND deleted = 0 "
            + "ORDER BY bill_date DESC LIMIT 400")
    List<LocalDate> recentBillDates(@Param("userId") Long userId);

    /**
     * 某一天支出合计（热力图 / 预算使用）。
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_bill "
            + "WHERE user_id = #{userId} AND type = 'EXPENSE' AND deleted = 0 AND bill_date = #{date}")
    BigDecimal sumExpenseOfDay(@Param("userId") Long userId, @Param("date") LocalDate date);

    /**
     * 分类在某月的支出合计（预算使用）。
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM t_bill "
            + "WHERE user_id = #{userId} AND type = 'EXPENSE' AND deleted = 0 "
            + "AND (category_id = #{categoryId} OR parent_category_id = #{categoryId}) "
            + "AND bill_date BETWEEN #{start} AND #{end}")
    BigDecimal sumExpenseOfCategory(@Param("userId") Long userId,
                                    @Param("categoryId") Long categoryId,
                                    @Param("start") LocalDate start,
                                    @Param("end") LocalDate end);
}
