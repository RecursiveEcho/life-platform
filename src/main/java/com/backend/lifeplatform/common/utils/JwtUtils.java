package com.backend.lifeplatform.common.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/** 生成登录访问令牌；后续接入 Spring Security 时由过滤器解析它。 */
@Component
public class JwtUtils {

    @Value("${jwt.secret:}")
    private String secretKey;

    /** 启动时校验密钥已通过环境变量注入，避免带着空密钥或内置默认值上线。 */
    @PostConstruct
    void validateSecret() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "未配置 JWT 密钥（jwt.secret）。请设置环境变量 JWT_SECRET 为一个足够随机的字符串后再启动。");
        }
    }

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    /**
     * 生成登录令牌。
     *
     * <p>subject 存 "userId:phone" 格式（见 {@link #parseClaims} 的解析侧），
     * role 单独放 claim 里，便于过滤器提取权限。</p>
     */
    public String generateToken(Long userId, String phone, String role) {
        return Jwts.builder()
                .setSubject(userId + ":" + phone)
                .claim("role", role)
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(SignatureAlgorithm.HS256, secretKey.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    /**
     * 校验签名和过期时间，并返回令牌中的全部声明。
     * 签名无效或已过期会抛出 io.jsonwebtoken 异常，由调用方捕获处理。
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody();
    }
}
