package com.demo.accessiblenav.auth;

import io.jsonwebtoken.Claims;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 为导航 WebSocket STOMP CONNECT 帧复用现有 JWT 认证。
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }
        if (accessor.getUser() != null && accessor.getUser().getName() != null
                && !accessor.getUser().getName().trim().isEmpty()) {
            return message;
        }

        String rawCredential = firstNonBlank(
                accessor.getFirstNativeHeader("Authorization"),
                accessor.getFirstNativeHeader("authorization"),
                accessor.getFirstNativeHeader("access_token")
        );
        if (rawCredential == null) {
            throw new AccessDeniedException("missing websocket authentication");
        }

        Claims claims;
        try {
            claims = jwtService.validateAccessToken(extractToken(rawCredential));
        } catch (RuntimeException ex) {
            throw new AccessDeniedException("invalid websocket authentication", ex);
        }

        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        if (isBlank(username) || isBlank(role)) {
            throw new AccessDeniedException("invalid websocket authentication");
        }

        accessor.setUser(new UsernamePasswordAuthenticationToken(
                username,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        ));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    private static String extractToken(String rawCredential) {
        String value = rawCredential.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        if (value.isEmpty()) {
            throw new AccessDeniedException("missing websocket authentication");
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
