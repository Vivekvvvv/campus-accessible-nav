package com.demo.accessiblenav.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API 速率限制过滤器
 * 支持两种模式：
 * - Redis 模式：分布式限流，多实例共享计数
 * - 本地模式：单实例限流，使用内存计数
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.max-requests:100}")
    private int maxRequests;

    @Value("${app.security.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${app.security.rate-limit.use-redis:false}")
    private boolean useRedis;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    // 本地限流计数器 (用于非 Redis 模式)
    private final Map<String, RateLimitEntry> localCounters = new ConcurrentHashMap<>();

    // 排除的路径 (不进行限流)
    private static final String[] EXCLUDED_PATHS = {
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs",
            "/api/public"
    };

    /**
     * 默认构造函数 (Spring 使用)
     */
    public RateLimitFilter() {
    }

    /**
     * 测试用构造函数
     */
    public RateLimitFilter(boolean enabled, int maxRequests, int windowSeconds,
                           boolean useRedis, RedisTemplate<String, String> redisTemplate) {
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.useRedis = useRedis;
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // 检查是否为排除路径
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        String clientKey = getClientKey(request);
        RateLimitResult result = checkRateLimit(clientKey);

        // 添加限流头信息
        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetTime));

        if (!result.allowed) {
            log.warn("速率限制触发: clientKey={}, path={}", clientKey, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"请求过于频繁，请稍后再试\", \"code\": 429}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 检查速率限制
     */
    private RateLimitResult checkRateLimit(String clientKey) {
        if (useRedis && redisTemplate != null) {
            return checkRedisRateLimit(clientKey);
        } else {
            return checkLocalRateLimit(clientKey);
        }
    }

    /**
     * Redis 分布式限流
     */
    private RateLimitResult checkRedisRateLimit(String clientKey) {
        try {
            String redisKey = RATE_LIMIT_PREFIX + clientKey;
            Long currentCount = redisTemplate.opsForValue().increment(redisKey);

            if (currentCount == null) {
                currentCount = 1L;
            }

            // 首次请求时设置过期时间
            if (currentCount == 1) {
                redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
            }

            // 获取剩余时间
            Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            long resetTime = System.currentTimeMillis() / 1000 + (ttl != null ? ttl : windowSeconds);

            int remaining = Math.max(0, (int) (maxRequests - currentCount));
            boolean allowed = currentCount <= maxRequests;

            return new RateLimitResult(allowed, remaining, resetTime);
        } catch (Exception e) {
            log.warn("Redis 限流失败，降级到本地限流: {}", e.getMessage());
            return checkLocalRateLimit(clientKey);
        }
    }

    /**
     * 本地内存限流
     */
    private RateLimitResult checkLocalRateLimit(String clientKey) {
        long now = System.currentTimeMillis();
        long windowStart = now - (windowSeconds * 1000L);

        // 清理过期的计数器
        cleanupExpiredEntries(windowStart);

        RateLimitEntry entry = localCounters.computeIfAbsent(clientKey,
                k -> new RateLimitEntry(now));

        // 如果窗口已过期，重置计数器
        if (entry.windowStart < windowStart) {
            entry.reset(now);
        }

        int currentCount = entry.count.incrementAndGet();
        int remaining = Math.max(0, maxRequests - currentCount);
        long resetTime = (entry.windowStart + windowSeconds * 1000L) / 1000;
        boolean allowed = currentCount <= maxRequests;

        return new RateLimitResult(allowed, remaining, resetTime);
    }

    /**
     * 清理过期的本地计数器
     */
    private void cleanupExpiredEntries(long windowStart) {
        localCounters.entrySet().removeIf(entry ->
                entry.getValue().windowStart < windowStart);
    }

    /**
     * 获取客户端标识
     * 优先使用用户 ID，否则使用 IP 地址
     */
    private String getClientKey(HttpServletRequest request) {
        // 优先使用认证过滤器显式注入的用户标识
        Object userId = request.getAttribute("userId");
        if (userId != null) {
            return "user:" + userId;
        }

        // 回退到 SecurityContext，兼容 JWT / API Key 等多种认证来源
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String principalName = authentication.getName();
            if (principalName != null && !principalName.trim().isEmpty()
                    && !"anonymousUser".equalsIgnoreCase(principalName)) {
                return "user:" + principalName.trim();
            }
        }

        // 获取真实 IP (考虑代理)
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 取第一个 IP (如果有多个)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return "ip:" + ip;
    }

    /**
     * 限流结果
     */
    private static class RateLimitResult {
        final boolean allowed;
        final int remaining;
        final long resetTime;

        RateLimitResult(boolean allowed, int remaining, long resetTime) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.resetTime = resetTime;
        }
    }

    /**
     * 本地限流计数器条目
     */
    private static class RateLimitEntry {
        volatile long windowStart;
        final AtomicInteger count;

        RateLimitEntry(long windowStart) {
            this.windowStart = windowStart;
            this.count = new AtomicInteger(0);
        }

        void reset(long newWindowStart) {
            this.windowStart = newWindowStart;
            this.count.set(0);
        }
    }
}
