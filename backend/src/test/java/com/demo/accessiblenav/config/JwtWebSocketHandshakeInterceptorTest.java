package com.demo.accessiblenav.config;

import com.demo.accessiblenav.auth.JwtService;
import com.demo.accessiblenav.auth.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtWebSocketHandshakeInterceptorTest {

    private static final String SECRET = "dev-secret-change-me-please-32-chars";

    private JwtService jwtService;
    private JwtWebSocketHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 15, 60);
        interceptor = new JwtWebSocketHandshakeInterceptor(jwtService);
    }

    @Test
    void shouldAcceptBearerTokenFromAuthorizationHeader() {
        String token = jwtService.generateToken("alice", UserRole.USER);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/navigation");
        servletRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        Map<String, Object> attributes = new HashMap<>();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                noopHandler(),
                attributes);

        assertTrue(allowed);
        assertEquals("alice", attributes.get(JwtWebSocketHandshakeInterceptor.ATTR_PRINCIPAL_NAME));
    }

    @Test
    void shouldAcceptAccessTokenQueryParameter() {
        String token = jwtService.generateToken("bob", UserRole.ADMIN);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/navigation");
        servletRequest.setQueryString("access_token=" + token);
        servletRequest.setParameter("access_token", token);

        Map<String, Object> attributes = new HashMap<>();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                noopHandler(),
                attributes);

        assertTrue(allowed);
        assertEquals("bob", attributes.get(JwtWebSocketHandshakeInterceptor.ATTR_PRINCIPAL_NAME));
    }

    @Test
    void shouldRejectHandshakeWhenTokenMissing() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/navigation");
        Map<String, Object> attributes = new HashMap<>();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                noopHandler(),
                attributes);

        assertFalse(allowed);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResponse.getStatus());
    }

    @Test
    void shouldRejectHandshakeWhenTokenInvalid() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/navigation");
        servletRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-token");
        Map<String, Object> attributes = new HashMap<>();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                noopHandler(),
                attributes);

        assertFalse(allowed);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResponse.getStatus());
    }

    private TextWebSocketHandler noopHandler() {
        return new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                // no-op
            }
        };
    }
}
