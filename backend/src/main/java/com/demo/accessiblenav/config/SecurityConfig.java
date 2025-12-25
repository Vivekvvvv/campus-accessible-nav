package com.demo.accessiblenav.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.core.env.Environment;

import com.demo.accessiblenav.auth.JwtAuthenticationFilter;
import com.demo.accessiblenav.apikey.ApiKeyAuthFilter;
import com.demo.accessiblenav.security.RateLimitFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter,
                                           ApiKeyAuthFilter apiKeyAuthFilter,
                                           RateLimitFilter rateLimitFilter,
                                           Environment env) throws Exception {
        boolean prometheusPublic = Boolean.parseBoolean(
                env.getProperty("app.security.actuator.prometheus-public", "true")
        );

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // OPTIONS 请求允许
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    // 认证相关接口公开
                    auth.requestMatchers("/api/auth/**").permitAll();
                    // SSE 事件流公开
                    auth.requestMatchers("/api/events").permitAll();
                    // 路由计算接口公开
                    auth.requestMatchers("/api/route").permitAll();
                    // 图数据查询接口公开
                    auth.requestMatchers("/api/graph/**").permitAll();
                    // 文件上传和访问
                    auth.requestMatchers("/api/files/upload/**").permitAll();
                    auth.requestMatchers("/uploads/**").permitAll();
                    // API 文档公开
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();
                    // Open API endpoints require API-key based auth context.
                    auth.requestMatchers("/api/open/**").authenticated();
                    // 障碍上报需要登录
                    auth.requestMatchers("/api/obstacles/report").hasAnyRole("USER", "ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    auth.requestMatchers("/api/obstacles/reports/me").hasAnyRole("USER", "ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    auth.requestMatchers("/api/navigation/session/**").hasAnyRole("USER", "ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    auth.requestMatchers("/api/v1/emergency/**").hasAnyRole("USER", "ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    auth.requestMatchers("/api/profile/**").hasAnyRole("USER", "ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    auth.requestMatchers("/api/favorites/**").hasAnyRole("USER", "ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    auth.requestMatchers("/api/v1/voice-settings/**").hasAnyRole("USER", "ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    // 后台管理域默认收敛到管理员；较宽角色访问必须逐条显式声明，避免新接口意外暴露。
                    auth.requestMatchers("/api/admin/users/**", "/api/admin/api-keys/**").hasRole("ADMIN");
                    auth.requestMatchers("/api/admin/obstacles/**", "/api/admin/logs/**").hasAnyRole("ADMIN", "REVIEWER");
                    auth.requestMatchers("/api/admin/ping", "/api/admin/profile").hasAnyRole("ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    auth.requestMatchers("/api/admin/graph/bulk-import/preview").hasAnyRole("ADMIN", "REVIEWER", "EDITOR");
                    auth.requestMatchers(HttpMethod.POST, "/api/admin/graph/bulk-import").hasAnyRole("ADMIN", "EDITOR");
                    auth.requestMatchers(HttpMethod.GET, "/api/admin/route/weights/**").hasAnyRole("ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    auth.requestMatchers(HttpMethod.PUT, "/api/admin/route/weights/**").hasAnyRole("ADMIN", "EDITOR");
                    auth.requestMatchers(HttpMethod.GET, "/api/admin/graph/changes/**").hasAnyRole("ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    auth.requestMatchers(HttpMethod.POST,
                            "/api/admin/graph/changes/*/review",
                            "/api/admin/graph/changes/*/approve",
                            "/api/admin/graph/changes/*/reject").hasAnyRole("ADMIN", "REVIEWER");
                    auth.requestMatchers(HttpMethod.POST, "/api/admin/graph/changes").hasAnyRole("ADMIN", "EDITOR");
                    auth.requestMatchers(HttpMethod.POST,
                            "/api/admin/graph/import",
                            "/api/admin/graph/replace",
                            "/api/admin/graph/repair").hasAnyRole("ADMIN", "EDITOR");
                    auth.requestMatchers(HttpMethod.POST, "/api/admin/graph/preview").hasAnyRole("ADMIN", "REVIEWER", "EDITOR");
                    auth.requestMatchers(HttpMethod.POST, "/api/admin/graph/rollback/*").hasRole("ADMIN");
                    auth.requestMatchers(HttpMethod.GET,
                            "/api/admin/graph/snapshot",
                            "/api/admin/graph/validate",
                            "/api/admin/graph/versions",
                            "/api/admin/graph/snapshots/*/diff/*").hasAnyRole("ADMIN", "REVIEWER", "EDITOR", "VIEWER");
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    // Actuator 端点保护
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();

                    // Prometheus scrape: allow public in dev by default; tighten in prod via config.
                    if (prometheusPublic) {
                        auth.requestMatchers("/actuator/prometheus").permitAll();
                    } else {
                        auth.requestMatchers("/actuator/prometheus").hasRole("ADMIN");
                    }

                    auth.requestMatchers("/actuator/**").hasRole("ADMIN");
                    // 其他 API 接口公开
                    auth.requestMatchers("/api/**").permitAll();
                    auth.anyRequest().permitAll();
                });

        http
                // JWT 认证过滤器
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // API Key auth for /api/open/**
                .addFilterAfter(apiKeyAuthFilter, JwtAuthenticationFilter.class)
                // 限流依赖认证上下文，放在 JWT/API Key 之后。
                .addFilterAfter(rateLimitFilter, ApiKeyAuthFilter.class);
        return http.build();
    }
}
