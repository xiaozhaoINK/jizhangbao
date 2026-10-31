package com.jizhangbao.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jizhangbao.common.api.PageResult;
import com.jizhangbao.common.exception.BizException;
import com.jizhangbao.common.exception.ErrorCode;
import com.jizhangbao.module.admin.dto.AdminOverviewVO;
import com.jizhangbao.module.admin.dto.AdminUserVO;
import com.jizhangbao.module.ai.entity.AiUsageLog;
import com.jizhangbao.module.ai.mapper.AiUsageLogMapper;
import com.jizhangbao.module.bill.entity.Bill;
import com.jizhangbao.module.bill.mapper.BillMapper;
import com.jizhangbao.module.category.entity.Category;
import com.jizhangbao.module.category.mapper.CategoryMapper;
import com.jizhangbao.module.report.entity.MonthlyReport;
import com.jizhangbao.module.report.mapper.MonthlyReportMapper;
import com.jizhangbao.module.user.entity.User;
import com.jizhangbao.module.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 后台管理服务：平台数据概览、用户管理。
 */
@Service
public class AdminService {

    private final UserMapper userMapper;
    private final BillMapper billMapper;
    private final CategoryMapper categoryMapper;
    private final MonthlyReportMapper reportMapper;
    private final AiUsageLogMapper usageLogMapper;

    public AdminService(UserMapper userMapper, BillMapper billMapper, CategoryMapper categoryMapper,
                        MonthlyReportMapper reportMapper, AiUsageLogMapper usageLogMapper) {
        this.userMapper = userMapper;
        this.billMapper = billMapper;
        this.categoryMapper = categoryMapper;
        this.reportMapper = reportMapper;
        this.usageLogMapper = usageLogMapper;
    }

    public AdminOverviewVO overview() {
        long userCount = count(userMapper.selectCount(new LambdaQueryWrapper<User>()));
        long activeUserCount = count(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)));
        long billCount = count(billMapper.selectCount(new LambdaQueryWrapper<Bill>()));
        long categoryCount = count(categoryMapper.selectCount(new LambdaQueryWrapper<Category>()));
        long reportCount = count(reportMapper.selectCount(new LambdaQueryWrapper<MonthlyReport>()));
        long aiCallCount = count(usageLogMapper.selectCount(new LambdaQueryWrapper<AiUsageLog>()));
        long todayAiCallCount = usageLogMapper.countSince(LocalDate.now().atStartOfDay());
        long todayNewUsers = count(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .ge(User::getCreatedAt, LocalDate.now().atStartOfDay())));

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        List<Map<String, Object>> rows = billMapper.selectMaps(new com.baomidou.mybatisplus.core.conditions.query
                .QueryWrapper<Bill>()
                .select("type", "COALESCE(SUM(amount), 0) AS total")
                .groupBy("type"));
        for (Map<String, Object> row : rows) {
            String type = String.valueOf(row.get("type"));
            BigDecimal total = new BigDecimal(String.valueOf(row.get("total")));
            if ("INCOME".equalsIgnoreCase(type)) {
                totalIncome = total;
            } else {
                totalExpense = total;
            }
        }
        return new AdminOverviewVO(userCount, activeUserCount, billCount, categoryCount,
                totalExpense, totalIncome, reportCount, aiCallCount, todayAiCallCount, todayNewUsers);
    }

    public PageResult<AdminUserVO> users(int page, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, kw).or().like(User::getNickname, kw));
        }
        Page<User> result = userMapper.selectPage(new Page<>(Math.max(1, page), Math.min(size, 100)), wrapper);
        List<AdminUserVO> records = new ArrayList<>();
        for (User user : result.getRecords()) {
            long bills = count(billMapper.selectCount(new LambdaQueryWrapper<Bill>()
                    .eq(Bill::getUserId, user.getId())));
            records.add(new AdminUserVO(user.getId(), user.getUsername(), user.getNickname(), user.getRole(),
                    user.getStatus(), bills, user.getLastLogin(), user.getCreatedAt()));
        }
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional
    public void updateUserStatus(Long operatorId, Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getId().equals(operatorId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能禁用自己");
        }
        user.setStatus(status != null && status == 1 ? 1 : 0);
        userMapper.updateById(user);
    }

    public List<Map<String, Object>> aiUsage() {
        return usageLogMapper.groupByScene();
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }

    public LocalDateTime now() {
        return LocalDateTime.now();
    }
}
