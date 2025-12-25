package com.demo.accessiblenav.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 安全响应头配置
 * 添加各种安全相关的HTTP响应头
 */
@Configuration
public class SecurityHeadersConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new SecurityHeadersInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/events"); // SSE 排除
    }

    /**
     * 安全响应头拦截器
     */
    private static class SecurityHeadersInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
            // X-Content-Type-Options: 防止MIME类型嗅探
            response.setHeader("X-Content-Type-Options", "nosniff");

            // X-Frame-Options: 防止点击劫持
            response.setHeader("X-Frame-Options", "DENY");

            // X-XSS-Protection: 启用浏览器XSS过滤器（旧版浏览器）
            response.setHeader("X-XSS-Protection", "1; mode=block");

            // Referrer-Policy: 控制Referer头
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

            // Permissions-Policy: 禁用不必要的浏览器功能
            response.setHeader("Permissions-Policy",
                    "accelerometer=(), camera=(), geolocation=(self), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()");

            // Cache-Control: API响应不缓存
            if (request.getRequestURI().startsWith("/api/")) {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
                response.setHeader("Pragma", "no-cache");
            }

            return true;
        }
    }
}
