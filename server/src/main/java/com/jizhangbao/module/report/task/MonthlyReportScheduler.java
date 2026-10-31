package com.jizhangbao.module.report.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jizhangbao.config.AppProperties;
import com.jizhangbao.module.report.service.MonthlyReportService;
import com.jizhangbao.module.user.entity.User;
import com.jizhangbao.module.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 月度报告定时任务：每月 1 日凌晨自动为所有用户生成上一个月的消费分析报告。
 */
@Component
public class MonthlyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportScheduler.class);

    private final MonthlyReportService reportService;
    private final UserMapper userMapper;
    private final AppProperties properties;

    public MonthlyReportScheduler(MonthlyReportService reportService, UserMapper userMapper,
                                  AppProperties properties) {
        this.reportService = reportService;
        this.userMapper = userMapper;
        this.properties = properties;
    }

    @Scheduled(cron = "${jizhangbao.report.cron:0 0 2 1 * ?}")
    public void generateMonthlyReports() {
        if (!properties.getReport().isEnabled()) {
            log.info("月度报告定时任务已关闭");
            return;
        }
        String month = MonthlyReportService.previousMonth();
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getStatus, 1)
                .select(User::getId));
        int success = 0;
        int skipped = 0;
        for (User user : users) {
            try {
                reportService.generate(user.getId(), month);
                success++;
            } catch (Exception e) {
                skipped++;
                log.debug("用户 {} 的 {} 报告生成跳过: {}", user.getId(), month, e.getMessage());
            }
        }
        log.info("月度报告定时任务完成：月份={}，成功={}，跳过={}", month, success, skipped);
    }
}
