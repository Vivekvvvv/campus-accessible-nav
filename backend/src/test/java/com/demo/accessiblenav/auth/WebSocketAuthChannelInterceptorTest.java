package com.demo.accessiblenav.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSocketAuthChannelInterceptorTest {

    private static final String SECRET = "dev-secret-change-me-please-32-chars";

    private JwtService jwtService;
    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 15, 60);
        interceptor = new WebSocketAuthChannelInterceptor(jwtService);
    }

    @Test
    void connectFrameShouldAuthenticateFromAuthorizationHeader() {
        String token = jwtService.generateToken("alice", UserRole.USER);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(message, new ExecutorSubscribableChannel());
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);

        Authentication authentication = (Authentication) resultAccessor.getUser();
        assertEquals("alice", authentication.getName());
        assertEquals("ROLE_USER", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void connectFrameShouldAuthenticateFromAccessTokenHeader() {
        String token = jwtService.generateToken("bob", UserRole.ADMIN);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("access_token", token);
        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(message, new ExecutorSubscribableChannel());
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);

        Authentication authentication = (Authentication) resultAccessor.getUser();
        assertEquals("bob", authentication.getName());
        assertEquals("ROLE_ADMIN", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void connectFrameShouldRejectMissingCredentials() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class,
                () -> interceptor.preSend(message, new ExecutorSubscribableChannel()));
    }

    @Test
    void nonConnectFrameShouldPassThroughUntouched() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, new ExecutorSubscribableChannel());

        assertSame(message, result);
    }
}
