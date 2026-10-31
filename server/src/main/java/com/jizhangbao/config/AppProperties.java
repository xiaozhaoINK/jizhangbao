package com.jizhangbao.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用自定义配置（前缀 {@code jizhangbao}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jizhangbao")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Ai ai = new Ai();
    private Cors cors = new Cors();
    private Demo demo = new Demo();
    private Report report = new Report();

    @Data
    public static class Jwt {
        private String secret = "jizhangbao-super-secret-key-please-change-in-production-2025";
        private long expireHours = 168;
        private String header = "Authorization";
        private String prefix = "Bearer ";
    }

    @Data
    public static class Ai {
        /** rule = 本地规则引擎，llm = Spring AI 调用大模型 */
        private String mode = "rule";
        private String model = "deepseek-chat";
        private String provider = "deepseek";
        private int timeoutSeconds = 60;
        private int maxHistoryMessages = 10;
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "*";
    }

    @Data
    public static class Demo {
        private boolean enabled = true;
        private String username = "demo";
        private String password = "123456";
        private String adminUsername = "admin";
        private String adminPassword = "123456";
        private int billDays = 200;
    }

    @Data
    public static class Report {
        private String cron = "0 0 2 1 * ?";
        private boolean enabled = true;
    }
}
