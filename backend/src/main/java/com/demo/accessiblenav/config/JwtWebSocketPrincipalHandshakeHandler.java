package com.demo.accessiblenav.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * 将握手阶段解析出的用户名绑定为 WebSocket Principal。
 */
@Component
public class JwtWebSocketPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        String username = (String) attributes.get(JwtWebSocketHandshakeInterceptor.ATTR_PRINCIPAL_NAME);
        if (!StringUtils.hasText(username)) {
            return null;
        }
        return () -> username;
    }
}
