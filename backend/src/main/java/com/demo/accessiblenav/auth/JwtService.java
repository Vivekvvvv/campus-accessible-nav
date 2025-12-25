package com.demo.accessiblenav.auth;

import com.demo.accessiblenav.auth.dto.TokenPair;
import com.demo.accessiblenav.exception.AuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    // 刷新阈值：当令牌剩余有效期少于此值时自动刷新 (默认5分钟)
    private static final long REFRESH_THRESHOLD_MILLIS = 5 * 60 * 1000L;

    private final SecretKey key;
    private final long accessTokenExpirationMillis;
    private final long refreshTokenExpirationMillis;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration-minutes:15}") long accessTokenExpirationMinutes,
            @Value("${app.security.jwt.refresh-expiration-minutes:10080}") long refreshTokenExpirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMillis = accessTokenExpirationMinutes * 60_000L;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMinutes * 60_000L;
    }

    /**
     * 生成令牌对（访问令牌 + 刷新令牌）
     */
    public TokenPair generateTokenPair(String username, UserRole role) {
        String accessToken = createToken(username, role, TOKEN_TYPE_ACCESS, accessTokenExpirationMillis);
        String refreshToken = createToken(username, role, TOKEN_TYPE_REFRESH, refreshTokenExpirationMillis);

        return new TokenPair(
                accessToken,
                refreshToken,
                accessTokenExpirationMillis / 1000,
                refreshTokenExpirationMillis / 1000
        );
    }

    /**
     * 生成单个访问令牌（兼容旧接口）
     */
    public String generateToken(String username, UserRole role) {
        return createToken(username, role, TOKEN_TYPE_ACCESS, accessTokenExpirationMillis);
    }

    /**
     * 使用刷新令牌获取新的令牌对
     */
    public TokenPair refreshAccessToken(String refreshToken) {
        try {
            Claims claims = parseToken(refreshToken);

            // 验证是否为刷新令牌
            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!TOKEN_TYPE_REFRESH.equals(tokenType)) {
                throw AuthenticationException.refreshTokenInvalid();
            }

            String username = claims.getSubject();
            String roleStr = claims.get("role", String.class);
            UserRole role = UserRole.valueOf(roleStr);

            return generateTokenPair(username, role);

        } catch (ExpiredJwtException e) {
            log.warn("刷新令牌已过期: {}", e.getMessage());
            throw AuthenticationException.refreshTokenExpired();
        } catch (JwtException e) {
            log.warn("刷新令牌无效: {}", e.getMessage());
            throw AuthenticationException.refreshTokenInvalid();
        }
    }

    /**
     * 验证并解析令牌
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证访问令牌
     */
    public Claims validateAccessToken(String token) {
        try {
            Claims claims = parseToken(token);

            // 验证是否为访问令牌
            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
                throw AuthenticationException.tokenInvalid();
            }

            return claims;

        } catch (ExpiredJwtException e) {
            throw AuthenticationException.tokenExpired();
        } catch (JwtException e) {
            throw AuthenticationException.tokenInvalid();
        }
    }

    /**
     * 检查令牌是否需要刷新
     * 当令牌剩余有效期少于阈值时返回 true
     */
    public boolean shouldRefresh(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            long remainingMillis = expiration.getTime() - System.currentTimeMillis();
            return remainingMillis > 0 && remainingMillis < REFRESH_THRESHOLD_MILLIS;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从令牌中获取用户信息并生成新的访问令牌
     * 用于无感刷新
     */
    public String refreshFromAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            String username = claims.getSubject();
            String roleStr = claims.get("role", String.class);
            UserRole role = UserRole.valueOf(roleStr);
            return generateToken(username, role);
        } catch (Exception e) {
            log.warn("无法从访问令牌刷新: {}", e.getMessage());
            return null;
        }
    }

    public long getExpirationMillis() {
        return accessTokenExpirationMillis;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationMillis / 1000;
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationMillis / 1000;
    }

    private String createToken(String username, UserRole role, String tokenType, long expirationMillis) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(expirationMillis);

        return Jwts.builder()
                .setSubject(username)
                // JJWT serializes iat/exp as NumericDate (seconds). Add a unique id so
                // tokens created within the same second are still distinct.
                .setId(UUID.randomUUID().toString())
                .claim("role", role.name())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
