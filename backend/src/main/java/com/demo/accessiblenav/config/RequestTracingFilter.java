package com.demo.accessiblenav.config;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪过滤器
 * 为每个请求生成唯一的追踪ID，用于日志关联
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_REQUEST_PATH = "requestPath";
    public static final String MDC_REQUEST_METHOD = "requestMethod";
    public static final String MDC_USER_IP = "userIp";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 生成或获取追踪ID
        String traceId = request.getHeader(TRACE_ID_HEADER);
        traceId = normalizeTraceId(traceId);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }

        // 设置MDC上下文
        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_REQUEST_PATH, request.getRequestURI());
        MDC.put(MDC_REQUEST_METHOD, request.getMethod());
        MDC.put(MDC_USER_IP, getClientIp(request));

        // 在响应头中返回追踪ID
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 清理MDC
            MDC.clear();
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String normalizeTraceId(String traceId) {
        if (traceId == null) {
            return null;
        }
        String normalized = traceId.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (!normalized.matches("[A-Za-z0-9_-]{6,64}")) {
            return null;
        }
        return normalized;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多个IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
