package com.jizhangbao.config;

import com.jizhangbao.security.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：登录拦截器 + 跨域。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final AppProperties properties;

    public WebConfig(JwtAuthInterceptor jwtAuthInterceptor, AppProperties properties) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/meta/**",
                        "/error"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String origins = properties.getCors().getAllowedOrigins();
        String[] allowed = (origins == null || origins.isBlank())
                ? new String[]{"*"}
                : origins.split(",");
        boolean wildcard = allowed.length == 1 && "*".equals(allowed[0].trim());
        registry.addMapping("/**")
                .allowedOriginPatterns(wildcard ? new String[]{"*"} : allowed)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization", "Content-Disposition")
                .allowCredentials(!wildcard)
                .maxAge(3600);
    }
}
