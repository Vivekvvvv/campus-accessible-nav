package com.demo.accessiblenav.auth;

import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 * 支持令牌无感刷新：当令牌即将过期时，自动生成新令牌并通过响应头返回
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // 响应头名称：新的访问令牌
    public static final String NEW_TOKEN_HEADER = "X-New-Access-Token";
    // 响应头名称：令牌过期时间（秒）
    public static final String TOKEN_EXPIRES_HEADER = "X-Token-Expires-In";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                Claims claims = jwtService.parseToken(token);
                String username = claims.getSubject();
                String role = String.valueOf(claims.get("role"));

                if (username != null && role != null) {
                    // 设置认证信息
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, Collections.singletonList(authority));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // 设置用户 ID 到请求属性 (供限流等其他过滤器使用)
                    request.setAttribute("userId", username);

                    // 检查是否需要无感刷新
                    if (jwtService.shouldRefresh(token)) {
                        String newToken = jwtService.refreshFromAccessToken(token);
                        if (newToken != null) {
                            response.setHeader(NEW_TOKEN_HEADER, newToken);
                            response.setHeader(TOKEN_EXPIRES_HEADER,
                                    String.valueOf(jwtService.getAccessTokenExpirationSeconds()));
                            log.debug("令牌即将过期，已自动刷新: user={}", username);
                        }
                    }
                }
            } catch (Exception e) {
                // Invalid token; continue without authentication
                log.debug("令牌验证失败: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}
