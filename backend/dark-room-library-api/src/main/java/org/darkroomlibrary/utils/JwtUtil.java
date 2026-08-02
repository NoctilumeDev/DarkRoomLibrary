package org.darkroomlibrary.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and validates application JWT access tokens.
 */
@Slf4j
@Component
public class JwtUtil {

    private final String secret;
    private final long expiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        this.secret = secret;
        this.expiration = expiration;
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token
     *
     * @param id   用户ID
     * @param role 用户角色
     * @param authVersion 认证状态版本
     * @return String
     */
    public String toToken(Integer id, Integer role, Integer authVersion) {
        return Jwts.builder()
                .header()
                .type("JWT")
                .and()
                .claim("id", id)
                .claim("role", role)
                .claim("authVersion", authVersion)
                .subject("用户认证")
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .id(UUID.randomUUID().toString())
                .signWith(getKey())
                .compact();
    }

    /**
     * 解密TOKEN，区分不同异常类型
     *
     * @param token token信息
     * @return JWT Claims，解析失败返回 null
     */
    public Claims fromToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        JwtParser jwtParser = Jwts.parser().verifyWith(getKey()).build();
        try {
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token已过期: {}", e.getMessage());
            return null;
        } catch (SignatureException e) {
            log.debug("Token签名无效: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("Token解析失败: {}", e.getMessage());
            return null;
        }
    }
}
