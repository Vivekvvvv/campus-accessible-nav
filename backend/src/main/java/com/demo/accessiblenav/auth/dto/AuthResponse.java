package com.demo.accessiblenav.auth.dto;

import com.demo.accessiblenav.auth.UserRole;

import java.time.Instant;

public class AuthResponse {

    private String token;          // 访问令牌（兼容旧字段）
    private String accessToken;    // 访问令牌
    private String refreshToken;   // 刷新令牌
    private String username;
    private UserRole role;
    private Instant expiresAt;
    private long accessTokenExpiresIn;   // 访问令牌过期时间（秒）
    private long refreshTokenExpiresIn;  // 刷新令牌过期时间（秒）
    private Integer creditScore;

    public AuthResponse() {
    }

    /**
     * 兼容旧版构造函数
     */
    public AuthResponse(String token, String username, UserRole role, Instant expiresAt, Integer creditScore) {
        this.token = token;
        this.accessToken = token;
        this.username = username;
        this.role = role;
        this.expiresAt = expiresAt;
        this.creditScore = creditScore;
    }

    /**
     * 新版构造函数，支持令牌对
     */
    public AuthResponse(String accessToken, String refreshToken, String username, UserRole role,
                        long accessTokenExpiresIn, long refreshTokenExpiresIn, Integer creditScore) {
        this.token = accessToken;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.username = username;
        this.role = role;
        this.expiresAt = Instant.now().plusSeconds(accessTokenExpiresIn);
        this.accessTokenExpiresIn = accessTokenExpiresIn;
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
        this.creditScore = creditScore;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public long getAccessTokenExpiresIn() {
        return accessTokenExpiresIn;
    }

    public void setAccessTokenExpiresIn(long accessTokenExpiresIn) {
        this.accessTokenExpiresIn = accessTokenExpiresIn;
    }

    public long getRefreshTokenExpiresIn() {
        return refreshTokenExpiresIn;
    }

    public void setRefreshTokenExpiresIn(long refreshTokenExpiresIn) {
        this.refreshTokenExpiresIn = refreshTokenExpiresIn;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore) {
        this.creditScore = creditScore;
    }
}
