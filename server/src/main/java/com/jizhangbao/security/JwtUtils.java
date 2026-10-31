package com.jizhangbao.security;

import com.jizhangbao.common.exception.BizException;
import com.jizhangbao.common.exception.ErrorCode;
import com.jizhangbao.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验。
 */
@Component
public class JwtUtils {

    private final AppProperties properties;
    private final SecretKey secretKey;

    public JwtUtils(AppProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jizhangbao.jwt.secret 长度必须不少于 32 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generate(Long userId, String username, String role) {
        long expireMillis = properties.getJwt().getExpireHours() * 3600_000L;
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(secretKey)
                .compact();
    }

    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class)
            );
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }

    /** token 剩余有效秒数，用于登出时设置黑名单 TTL */
    public long remainSeconds(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(secretKey).build()
                    .parseSignedClaims(token).getPayload();
            long remain = claims.getExpiration().getTime() - System.currentTimeMillis();
            return Math.max(remain / 1000, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public long expireSeconds() {
        return properties.getJwt().getExpireHours() * 3600L;
    }
}
