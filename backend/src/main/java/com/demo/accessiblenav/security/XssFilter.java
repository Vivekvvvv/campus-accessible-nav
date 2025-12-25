package com.demo.accessiblenav.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * XSS 过滤器
 * 对所有请求参数进行XSS净化
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class XssFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // 包装请求以净化参数
        XssHttpServletRequestWrapper wrappedRequest = new XssHttpServletRequestWrapper(request);
        filterChain.doFilter(wrappedRequest, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        // 排除静态资源和API文档
        return path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/uploads/") ||
               path.startsWith("/actuator/");
    }

    /**
     * 包装HttpServletRequest以净化参数
     */
    private static class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String[]> sanitizedParams;

        public XssHttpServletRequestWrapper(HttpServletRequest request) {
            super(request);
            this.sanitizedParams = sanitizeParameters(request.getParameterMap());
        }

        private Map<String, String[]> sanitizeParameters(Map<String, String[]> params) {
            Map<String, String[]> sanitized = new HashMap<>();
            for (Map.Entry<String, String[]> entry : params.entrySet()) {
                String key = XssSanitizer.sanitize(entry.getKey());
                String[] values = entry.getValue();
                if (values != null) {
                    String[] sanitizedValues = new String[values.length];
                    for (int i = 0; i < values.length; i++) {
                        sanitizedValues[i] = XssSanitizer.sanitize(values[i]);
                    }
                    sanitized.put(key, sanitizedValues);
                }
            }
            return sanitized;
        }

        @Override
        public String getParameter(String name) {
            String[] values = sanitizedParams.get(name);
            return values != null && values.length > 0 ? values[0] : null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.unmodifiableMap(sanitizedParams);
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(sanitizedParams.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            return sanitizedParams.get(name);
        }

        @Override
        public String getHeader(String name) {
            String value = super.getHeader(name);
            // 只净化可能包含用户输入的头
            if (value != null && isUserInputHeader(name)) {
                return XssSanitizer.sanitize(value);
            }
            return value;
        }

        private boolean isUserInputHeader(String name) {
            String lowerName = name.toLowerCase();
            // 这些头可能包含用户输入
            return lowerName.equals("referer") ||
                   lowerName.equals("user-agent") ||
                   lowerName.startsWith("x-");
        }
    }
}
