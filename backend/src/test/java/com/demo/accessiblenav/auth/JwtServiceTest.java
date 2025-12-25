package com.demo.accessiblenav.auth;

import com.demo.accessiblenav.auth.dto.TokenPair;
import com.demo.accessiblenav.exception.AuthenticationException;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtService 单元测试
 */
class JwtServiceTest {

    private JwtService jwtService;

    private static final String TEST_SECRET = "test-secret-key-for-unit-testing-32chars";
    private static final long ACCESS_TOKEN_EXPIRATION_MINUTES = 15;
    private static final long REFRESH_TOKEN_EXPIRATION_MINUTES = 60;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, ACCESS_TOKEN_EXPIRATION_MINUTES, REFRESH_TOKEN_EXPIRATION_MINUTES);
    }

    @Test
    @DisplayName("应该成功生成令牌对")
    void shouldGenerateTokenPair() {
        // Given
        String username = "testuser";
        UserRole role = UserRole.USER;

        // When
        TokenPair tokenPair = jwtService.generateTokenPair(username, role);

        // Then
        assertNotNull(tokenPair);
        assertNotNull(tokenPair.getAccessToken());
        assertNotNull(tokenPair.getRefreshToken());
        assertTrue(tokenPair.getAccessTokenExpiresIn() > 0);
        assertTrue(tokenPair.getRefreshTokenExpiresIn() > 0);
        assertNotEquals(tokenPair.getAccessToken(), tokenPair.getRefreshToken());
    }

    @Test
    @DisplayName("应该成功解析访问令牌")
    void shouldParseAccessToken() {
        // Given
        String username = "testuser";
        UserRole role = UserRole.ADMIN;
        TokenPair tokenPair = jwtService.generateTokenPair(username, role);

        // When
        Claims claims = jwtService.validateAccessToken(tokenPair.getAccessToken());

        // Then
        assertEquals(username, claims.getSubject());
        assertEquals(role.name(), claims.get("role", String.class));
        assertEquals("access", claims.get("type", String.class));
    }

    @Test
    @DisplayName("应该成功使用刷新令牌获取新令牌对")
    void shouldRefreshAccessToken() throws InterruptedException {
        // Given
        String username = "testuser";
        UserRole role = UserRole.USER;
        TokenPair originalTokenPair = jwtService.generateTokenPair(username, role);

        // 等待1秒，确保新令牌的时间戳不同
        Thread.sleep(1000);

        // When
        TokenPair newTokenPair = jwtService.refreshAccessToken(originalTokenPair.getRefreshToken());

        // Then
        assertNotNull(newTokenPair);
        assertNotNull(newTokenPair.getAccessToken());
        assertNotNull(newTokenPair.getRefreshToken());
        // 新令牌应该不同于原令牌
        assertNotEquals(originalTokenPair.getAccessToken(), newTokenPair.getAccessToken());
    }

    @Test
    @DisplayName("使用访问令牌作为刷新令牌应该抛出异常")
    void shouldThrowExceptionWhenUsingAccessTokenAsRefreshToken() {
        // Given
        String username = "testuser";
        UserRole role = UserRole.USER;
        TokenPair tokenPair = jwtService.generateTokenPair(username, role);

        // When & Then
        assertThrows(AuthenticationException.class, () -> {
            jwtService.refreshAccessToken(tokenPair.getAccessToken());
        });
    }

    @Test
    @DisplayName("无效令牌应该抛出异常")
    void shouldThrowExceptionForInvalidToken() {
        // Given
        String invalidToken = "invalid.token.here";

        // When & Then
        assertThrows(AuthenticationException.class, () -> {
            jwtService.validateAccessToken(invalidToken);
        });
    }

    @Test
    @DisplayName("应该正确返回过期时间")
    void shouldReturnCorrectExpirationTimes() {
        // When
        long accessTokenExpiration = jwtService.getAccessTokenExpirationSeconds();
        long refreshTokenExpiration = jwtService.getRefreshTokenExpirationSeconds();

        // Then
        assertEquals(ACCESS_TOKEN_EXPIRATION_MINUTES * 60, accessTokenExpiration);
        assertEquals(REFRESH_TOKEN_EXPIRATION_MINUTES * 60, refreshTokenExpiration);
    }

    @Test
    @DisplayName("不同用户应该生成不同的令牌")
    void shouldGenerateDifferentTokensForDifferentUsers() {
        // Given
        String user1 = "user1";
        String user2 = "user2";

        // When
        TokenPair tokenPair1 = jwtService.generateTokenPair(user1, UserRole.USER);
        TokenPair tokenPair2 = jwtService.generateTokenPair(user2, UserRole.USER);

        // Then
        assertNotEquals(tokenPair1.getAccessToken(), tokenPair2.getAccessToken());
        assertNotEquals(tokenPair1.getRefreshToken(), tokenPair2.getRefreshToken());
    }

    @Test
    @DisplayName("新令牌不需要刷新")
    void newTokenShouldNotNeedRefresh() {
        // Given
        String username = "testuser";
        TokenPair tokenPair = jwtService.generateTokenPair(username, UserRole.USER);

        // When
        boolean shouldRefresh = jwtService.shouldRefresh(tokenPair.getAccessToken());

        // Then
        assertFalse(shouldRefresh, "新生成的令牌不应该需要刷新");
    }

    @Test
    @DisplayName("无效令牌不需要刷新")
    void invalidTokenShouldNotNeedRefresh() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        boolean shouldRefresh = jwtService.shouldRefresh(invalidToken);

        // Then
        assertFalse(shouldRefresh, "无效令牌不应该需要刷新");
    }

    @Test
    @DisplayName("应该能从访问令牌刷新生成新令牌")
    void shouldRefreshFromAccessToken() {
        // Given
        String username = "testuser";
        UserRole role = UserRole.ADMIN;
        TokenPair tokenPair = jwtService.generateTokenPair(username, role);

        // When
        String newToken = jwtService.refreshFromAccessToken(tokenPair.getAccessToken());

        // Then
        assertNotNull(newToken, "应该成功生成新令牌");
        assertNotEquals(tokenPair.getAccessToken(), newToken, "新令牌应该不同于原令牌");

        // 验证新令牌包含正确的用户信息
        Claims newClaims = jwtService.validateAccessToken(newToken);
        assertEquals(username, newClaims.getSubject());
        assertEquals(role.name(), newClaims.get("role", String.class));
    }

    @Test
    @DisplayName("无效令牌刷新应该返回null")
    void refreshFromInvalidTokenShouldReturnNull() {
        // Given
        String invalidToken = "invalid.token.here";

        // When
        String newToken = jwtService.refreshFromAccessToken(invalidToken);

        // Then
        assertNull(newToken, "无效令牌刷新应该返回null");
    }
}
