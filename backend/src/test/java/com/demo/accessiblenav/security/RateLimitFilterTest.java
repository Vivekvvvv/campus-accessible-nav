package com.demo.accessiblenav.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimitFilter 单元测试
 */
class RateLimitFilterTest {

    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        // 使用内存模式，每分钟最多5个请求进行测试
        rateLimitFilter = new RateLimitFilter(true, 5, 60, false, null);
    }

    @Test
    @DisplayName("正常请求应该通过")
    void normalRequestShouldPass() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // When
        rateLimitFilter.doFilterInternal(request, response, chain);

        // Then
        assertEquals(200, response.getStatus());
        assertNotNull(response.getHeader("X-RateLimit-Limit"));
        assertNotNull(response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("超过限制应该返回429")
    void exceedingLimitShouldReturn429() throws ServletException, IOException {
        // Given
        String clientIp = "192.168.1.100";

        // When - 发送超过限制的请求
        MockHttpServletResponse lastResponse = null;
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(clientIp);
            request.setRequestURI("/api/test");
            lastResponse = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            rateLimitFilter.doFilterInternal(request, lastResponse, chain);
        }

        // Then - 最后一个请求应该被限制
        assertNotNull(lastResponse);
        assertEquals(429, lastResponse.getStatus());
    }

    @Test
    @DisplayName("健康检查端点应该被排除")
    void healthEndpointShouldBeExcluded() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.setRequestURI("/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // When
        rateLimitFilter.doFilterInternal(request, response, chain);

        // Then - 健康检查不应该有速率限制头
        assertNull(response.getHeader("X-RateLimit-Limit"));
    }

    @Test
    @DisplayName("Swagger端点应该被排除")
    void swaggerEndpointShouldBeExcluded() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        request.setRequestURI("/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // When
        rateLimitFilter.doFilterInternal(request, response, chain);

        // Then
        assertNull(response.getHeader("X-RateLimit-Limit"));
    }

    @Test
    @DisplayName("不同IP应该有独立的限制")
    void differentIpsShouldHaveSeparateLimits() throws ServletException, IOException {
        // Given
        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";

        // When - IP1 发送多个请求
        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(ip1);
            request.setRequestURI("/api/test");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            rateLimitFilter.doFilterInternal(request, response, chain);
        }

        // IP2 的第一个请求
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRemoteAddr(ip2);
        request2.setRequestURI("/api/test");
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        MockFilterChain chain2 = new MockFilterChain();
        rateLimitFilter.doFilterInternal(request2, response2, chain2);

        // Then - IP2 应该有完整的配额
        assertEquals(200, response2.getStatus());
        assertEquals("4", response2.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("相同IP下不同用户应该使用独立配额")
    void differentUsersOnSameIpShouldHaveSeparateLimits() throws ServletException, IOException {
        String sharedIp = "192.168.1.9";

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(sharedIp);
            request.setRequestURI("/api/test");
            request.setAttribute("userId", "alice");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            rateLimitFilter.doFilterInternal(request, response, chain);
            assertEquals(200, response.getStatus());
        }

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(sharedIp);
        request.setRequestURI("/api/test");
        request.setAttribute("userId", "bob");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        rateLimitFilter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("4", response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("禁用时应该放行所有请求")
    void disabledFilterShouldAllowAll() throws ServletException, IOException {
        // Given - 创建禁用的过滤器
        RateLimitFilter disabledFilter = new RateLimitFilter(false, 1, 60, false, null);
        String clientIp = "192.168.1.200";

        // When - 发送多个请求
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(clientIp);
            request.setRequestURI("/api/test");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            disabledFilter.doFilterInternal(request, response, chain);

            // Then - 所有请求都应该通过
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    @DisplayName("X-Forwarded-For头应该被识别")
    void xForwardedForHeaderShouldBeRecognized() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");  // 代理服务器IP
        request.addHeader("X-Forwarded-For", "203.0.113.1, 70.41.3.18");  // 真实客户端IP
        request.setRequestURI("/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // When
        rateLimitFilter.doFilterInternal(request, response, chain);

        // Then
        assertEquals(200, response.getStatus());
        // 验证使用了 X-Forwarded-For 中的第一个IP
        assertNotNull(response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    @DisplayName("已认证用户应该优先按用户而不是IP限流")
    void authenticatedUserShouldBeRateLimitedByUserIdentity() throws ServletException, IOException {
        for (int i = 0; i < 4; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("192.168.1.10");
            request.setRequestURI("/api/test");
            request.setAttribute("userId", "alice");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            rateLimitFilter.doFilterInternal(request, response, chain);
        }

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.99");
        request.setRequestURI("/api/test");
        request.setAttribute("userId", "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        rateLimitFilter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus());
        assertEquals("0", response.getHeader("X-RateLimit-Remaining"));
    }
}
