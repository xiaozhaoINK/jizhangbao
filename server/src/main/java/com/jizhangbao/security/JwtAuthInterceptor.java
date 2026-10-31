package com.jizhangbao.security;

import com.jizhangbao.common.util.CacheKeys;
import com.jizhangbao.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：解析 Authorization 头中的 JWT，校验黑名单，并把用户信息放入 {@link AuthContext}。
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthInterceptor.class);

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties properties;

    public JwtAuthInterceptor(JwtUtils jwtUtils, StringRedisTemplate redisTemplate, AppProperties properties) {
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeJson(response, 1002, "请先登录");
            return false;
        }
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(CacheKeys.tokenBlacklist(token)))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                writeJson(response, 2006, "登录已过期，请重新登录");
                return false;
            }
            AuthContext.set(jwtUtils.parse(token));
            return true;
        } catch (Exception e) {
            log.debug("鉴权失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeJson(response, 1002, e.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(properties.getJwt().getHeader());
        if (!StringUtils.hasText(header)) {
            return null;
        }
        String prefix = properties.getJwt().getPrefix();
        if (StringUtils.hasText(prefix) && header.startsWith(prefix)) {
            return header.substring(prefix.length()).trim();
        }
        return header.trim();
    }

    private void writeJson(HttpServletResponse response, int code, String message) {
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message
                    + "\",\"data\":null,\"timestamp\":" + System.currentTimeMillis() + "}");
            response.getWriter().flush();
        } catch (Exception ignored) {
            // 客户端可能已断开，忽略
        }
    }
}
