package com.jizhangbao;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 记账宝 —— 智能记账应用后端服务入口。
 *
 * <p>技术栈：Spring Boot 3.5 / MyBatis-Plus / Spring AI / MySQL / Redis</p>
 *
 * @author 记账宝
 */
@EnableAsync
@EnableScheduling
@MapperScan("com.jizhangbao.module.**.mapper")
@SpringBootApplication
public class JiZhangBaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiZhangBaoApplication.class, args);
    }
}
