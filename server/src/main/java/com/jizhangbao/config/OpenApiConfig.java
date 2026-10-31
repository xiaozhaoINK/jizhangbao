package com.jizhangbao.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置，访问 http://localhost:8080/doc.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jizhangbaoOpenApi() {
        String schemeName = "BearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("记账宝 API")
                        .version("1.0.0")
                        .description("智能记账应用服务端接口文档：记账 / 统计 / 预算 / AI 助手 / 消费洞察")
                        .contact(new Contact().name("记账宝").url("https://github.com/jizhangbao")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
