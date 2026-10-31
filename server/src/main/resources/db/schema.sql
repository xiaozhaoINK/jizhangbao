-- =====================================================================
--  记账宝 (JiZhangBao) 数据库结构  |  MySQL 8.0+ / 9.x
--  字符集：utf8mb4   存储引擎：InnoDB
--  所有业务表均带逻辑删除字段 deleted（0 未删除 / 1 已删除）
-- =====================================================================

CREATE TABLE IF NOT EXISTS `t_user`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    `password`    VARCHAR(120) NOT NULL COMMENT 'BCrypt 加密密码',
    `nickname`    VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(500)          DEFAULT NULL COMMENT '头像地址',
    `email`       VARCHAR(100)          DEFAULT NULL COMMENT '邮箱',
    `phone`       VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色 USER/ADMIN',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 1正常 0禁用',
    `last_login`  DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户表';

CREATE TABLE IF NOT EXISTS `t_category`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户ID',
    `parent_id`  BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类ID，0 表示一级分类',
    `name`       VARCHAR(30) NOT NULL COMMENT '分类名称',
    `type`       VARCHAR(10) NOT NULL COMMENT '类型 INCOME 收入 / EXPENSE 支出',
    `icon`       VARCHAR(50)          DEFAULT NULL COMMENT '图标名（前端映射）',
    `color`      VARCHAR(20)          DEFAULT NULL COMMENT '主题色 #RRGGBB',
    `sort`       INT         NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `is_system`     TINYINT     NOT NULL DEFAULT 0 COMMENT '是否系统内置分类 1是 0否',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_type` (`user_id`, `type`),
    KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='收支分类表（支持多级）';

CREATE TABLE IF NOT EXISTS `t_bill`
(
    `id`                 BIGINT         NOT NULL AUTO_INCREMENT COMMENT '账单ID',
    `user_id`            BIGINT         NOT NULL COMMENT '所属用户ID',
    `type`               VARCHAR(10)    NOT NULL COMMENT '类型 INCOME / EXPENSE',
    `category_id`        BIGINT         NOT NULL COMMENT '二级（或一级）分类ID',
    `parent_category_id` BIGINT         NOT NULL DEFAULT 0 COMMENT '一级分类ID（冗余，便于聚合统计）',
    `amount`             DECIMAL(12, 2) NOT NULL COMMENT '金额，单位元',
    `remark`             VARCHAR(255)            DEFAULT NULL COMMENT '备注',
    `bill_date`          DATE           NOT NULL COMMENT '账单日期',
    `bill_time`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记账时间',
    `pay_method`         VARCHAR(20)             DEFAULT NULL COMMENT '支付方式',
    `tags`               VARCHAR(200)            DEFAULT NULL COMMENT '标签，逗号分隔',
    `source`             VARCHAR(10)    NOT NULL DEFAULT 'MANUAL' COMMENT '来源 MANUAL 手动 / AI 智能记账',
    `ai_input`           VARCHAR(500)            DEFAULT NULL COMMENT 'AI 记账时的原始自然语言',
    `created_at`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`         DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`            TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `bill_date`),
    KEY `idx_user_type_date` (`user_id`, `type`, `bill_date`),
    KEY `idx_category` (`category_id`),
    KEY `idx_parent_category` (`parent_category_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='收支账单表';

CREATE TABLE IF NOT EXISTS `t_budget`
(
    `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '预算ID',
    `user_id`         BIGINT         NOT NULL COMMENT '所属用户ID',
    `bill_month`      CHAR(7)        NOT NULL COMMENT '预算月份 yyyy-MM',
    `category_id`     BIGINT         NOT NULL DEFAULT 0 COMMENT '分类ID，0 表示当月总预算',
    `amount`          DECIMAL(12, 2) NOT NULL COMMENT '预算金额',
    `alert_threshold` INT            NOT NULL DEFAULT 80 COMMENT '提醒阈值（百分比）',
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_month` (`user_id`, `bill_month`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='月度预算表';

CREATE TABLE IF NOT EXISTS `t_monthly_report`
(
    `id`            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '报告ID',
    `user_id`       BIGINT         NOT NULL COMMENT '所属用户ID',
    `bill_month`    CHAR(7)        NOT NULL COMMENT '报告月份 yyyy-MM',
    `total_income`  DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '当月收入合计',
    `total_expense` DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '当月支出合计',
    `balance`       DECIMAL(12, 2) NOT NULL DEFAULT 0 COMMENT '当月结余',
    `bill_count`    INT            NOT NULL DEFAULT 0 COMMENT '当月账单笔数',
    `top_category`  VARCHAR(50)             DEFAULT NULL COMMENT '支出最高分类',
    `content`       TEXT COMMENT '报告正文（Markdown）',
    `insights`      TEXT COMMENT '洞察要点 JSON 数组',
    `suggestions`   TEXT COMMENT '省钱建议 JSON 数组',
    `generator`     VARCHAR(20)    NOT NULL DEFAULT 'RULE' COMMENT '生成方式 LLM / RULE',
    `created_at`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`       TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_month` (`user_id`, `bill_month`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='月度消费分析报告';

CREATE TABLE IF NOT EXISTS `t_ai_conversation`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '会话ID',
    `user_id`    BIGINT      NOT NULL COMMENT '所属用户ID',
    `title`      VARCHAR(80) NOT NULL DEFAULT '新的对话' COMMENT '会话标题',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='AI 助手会话表';

CREATE TABLE IF NOT EXISTS `t_ai_message`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `conversation_id` BIGINT      NOT NULL COMMENT '会话ID',
    `user_id`         BIGINT      NOT NULL COMMENT '所属用户ID',
    `role`            VARCHAR(20) NOT NULL COMMENT '角色 user / assistant',
    `content`         TEXT COMMENT '消息内容',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted`         TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_conversation` (`conversation_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='AI 助手消息表';

CREATE TABLE IF NOT EXISTS `t_ai_usage_log`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
    `scene`       VARCHAR(30) NOT NULL COMMENT '场景 PARSE/CHAT/ANALYSIS/REPORT',
    `engine`      VARCHAR(20) NOT NULL COMMENT '引擎 LLM / RULE',
    `input_chars` INT         NOT NULL DEFAULT 0 COMMENT '输入字符数',
    `success`     TINYINT     NOT NULL DEFAULT 1 COMMENT '是否成功',
    `cost_ms`     INT         NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_scene` (`user_id`, `scene`),
    KEY `idx_created` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='AI 调用日志';
