package com.demo.accessiblenav.config;

import com.demo.accessiblenav.auth.WebSocketAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;

/**
 * WebSocket 配置
 * 支持实时导航、位置追踪、紧急求助等功能
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;
    private final boolean hasWildcardOrigin;
    private final JwtWebSocketHandshakeInterceptor handshakeInterceptor;
    private final JwtWebSocketPrincipalHandshakeHandler principalHandshakeHandler;
    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    public WebSocketConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins,
                           JwtWebSocketHandshakeInterceptor handshakeInterceptor,
                           JwtWebSocketPrincipalHandshakeHandler principalHandshakeHandler,
                           WebSocketAuthChannelInterceptor authChannelInterceptor) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        this.hasWildcardOrigin = Arrays.stream(this.allowedOrigins)
                .anyMatch(origin -> "*".equals(origin) || origin.contains("*"));
        this.handshakeInterceptor = handshakeInterceptor;
        this.principalHandshakeHandler = principalHandshakeHandler;
        this.authChannelInterceptor = authChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry config) {
        // 启用简单的内存消息代理
        // /topic 用于广播消息（如紧急通知）
        // /queue 用于点对点消息（如个人导航指令）
        config.enableSimpleBroker("/topic", "/queue");

        // 应用程序目标前缀，客户端发送消息时使用
        config.setApplicationDestinationPrefixes("/app");

        // 用户目标前缀，用于发送私人消息
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        // 注册 WebSocket 端点
        var endpoint = registry.addEndpoint("/ws/navigation")
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(principalHandshakeHandler);
        if (allowedOrigins.length == 0 || hasWildcardOrigin) {
            endpoint.setAllowedOriginPatterns(allowedOrigins.length == 0 ? new String[]{"*"} : allowedOrigins);
        } else {
            endpoint.setAllowedOrigins(allowedOrigins);
        }
        endpoint.withSockJS().setInterceptors(handshakeInterceptor); // 支持 SockJS 回退
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
