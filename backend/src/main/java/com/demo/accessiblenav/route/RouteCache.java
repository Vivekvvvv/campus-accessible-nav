package com.demo.accessiblenav.route;

import com.demo.accessiblenav.route.dto.RouteResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能两级路由缓存
 * L1: Caffeine 本地缓存（快速响应）
 * L2: Redis 分布式缓存（多实例共享）
 */
@Component
public class RouteCache {

    private static final Logger log = LoggerFactory.getLogger(RouteCache.class);

    // L1: Caffeine 精确匹配缓存
    private Cache<String, RouteResponse> exactCache;

    // L1: Caffeine 网格化缓存（用于相近起终点）
    private Cache<String, RouteResponse> gridCache;

    // L2: Redis 分布式缓存（可选）
    @Autowired(required = false)
    private RedisTemplate<String, RouteResponse> routeCacheRedisTemplate;

    @Value("${route.cache.exact.max-size:10000}")
    private int exactCacheMaxSize;

    @Value("${route.cache.exact.expire-minutes:30}")
    private int exactCacheExpireMinutes;

    @Value("${route.cache.grid.max-size:50000}")
    private int gridCacheMaxSize;

    @Value("${route.cache.grid.expire-minutes:120}")
    private int gridCacheExpireMinutes;

    @Value("${route.cache.grid.size-meters:50}")
    private int gridSizeMeters;

    @Value("${route.cache.redis.enabled:false}")
    private boolean redisEnabled;

    @Value("${route.cache.redis.expire-minutes:30}")
    private int redisExpireMinutes;

    @Value("${route.cache.redis.key-prefix:route:}")
    private String redisKeyPrefix;

    // 缓存统计
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong l1ExactHits = new AtomicLong(0);
    private final AtomicLong l1GridHits = new AtomicLong(0);
    private final AtomicLong l2Hits = new AtomicLong(0);

    @PostConstruct
    public void init() {
        exactCache = Caffeine.newBuilder()
                .maximumSize(exactCacheMaxSize)
                .expireAfterWrite(Duration.ofMinutes(exactCacheExpireMinutes))
                .recordStats()
                .build();

        gridCache = Caffeine.newBuilder()
                .maximumSize(gridCacheMaxSize)
                .expireAfterWrite(Duration.ofMinutes(gridCacheExpireMinutes))
                .recordStats()
                .build();

        boolean redisAvailable = redisEnabled && routeCacheRedisTemplate != null;
        log.info("RouteCache 初始化完成: L1[exact={}, grid={}], L2[redis={}, available={}]",
                exactCacheMaxSize, gridCacheMaxSize, redisEnabled, redisAvailable);
    }

    /**
     * 获取缓存的路由结果
     * 查找顺序: L1精确 -> L1网格 -> L2 Redis
     */
    public RouteResponse get(String key) {
        totalRequests.incrementAndGet();

        // 1. L1: 尝试精确匹配
        RouteResponse exact = exactCache.getIfPresent(key);
        if (exact != null) {
            l1ExactHits.incrementAndGet();
            return exact;
        }

        // 2. L1: 尝试网格匹配
        String gridKey = toGridKey(key);
        if (gridKey != null) {
            RouteResponse gridResult = gridCache.getIfPresent(gridKey);
            if (gridResult != null) {
                l1GridHits.incrementAndGet();
                return gridResult;
            }
        }

        // 3. L2: 尝试从 Redis 获取
        if (isRedisAvailable()) {
            try {
                String redisKey = redisKeyPrefix + key;
                RouteResponse redisResult = routeCacheRedisTemplate.opsForValue().get(redisKey);
                if (redisResult != null) {
                    l2Hits.incrementAndGet();
                    // 回填到 L1 缓存
                    exactCache.put(key, redisResult);
                    if (gridKey != null) {
                        gridCache.put(gridKey, redisResult);
                    }
                    log.debug("L2 Redis 命中，回填到 L1: key={}", key);
                    return redisResult;
                }
            } catch (Exception e) {
                log.warn("Redis 读取失败，降级到仅使用 L1 缓存: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 存储路由结果到缓存
     * 同时写入 L1 和 L2
     */
    public void put(String key, RouteResponse value) {
        // L1: 存入 Caffeine 精确缓存
        exactCache.put(key, value);

        // L1: 同时存入网格缓存
        String gridKey = toGridKey(key);
        if (gridKey != null) {
            gridCache.put(gridKey, value);
        }

        // L2: 异步存入 Redis
        if (isRedisAvailable()) {
            try {
                String redisKey = redisKeyPrefix + key;
                routeCacheRedisTemplate.opsForValue().set(
                        redisKey, Objects.requireNonNull(value), redisExpireMinutes, TimeUnit.MINUTES);
                log.debug("写入 L2 Redis: key={}", redisKey);
            } catch (Exception e) {
                log.warn("Redis 写入失败，仅使用 L1 缓存: {}", e.getMessage());
            }
        }
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        // 清空 L1
        exactCache.invalidateAll();
        gridCache.invalidateAll();

        // 清空 L2 Redis
        if (isRedisAvailable()) {
            try {
                Set<String> keys = routeCacheRedisTemplate.keys(redisKeyPrefix + "*");
                if (keys != null && !keys.isEmpty()) {
                    routeCacheRedisTemplate.delete(keys);
                    log.info("清空 L2 Redis 缓存: {} 个键", keys.size());
                }
            } catch (Exception e) {
                log.warn("Redis 清空失败: {}", e.getMessage());
            }
        }

        // 重置统计
        totalRequests.set(0);
        l1ExactHits.set(0);
        l1GridHits.set(0);
        l2Hits.set(0);

        log.info("RouteCache 已清空 (L1 + L2)");
    }

    /**
     * 判断 Redis 是否可用
     */
    private boolean isRedisAvailable() {
        return redisEnabled && routeCacheRedisTemplate != null;
    }

    /**
     * 获取总缓存命中率
     */
    public double getHitRatio() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return (l1ExactHits.get() + l1GridHits.get() + l2Hits.get()) / (double) total;
    }

    /**
     * 获取 L1 命中率
     */
    public double getL1HitRatio() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return (l1ExactHits.get() + l1GridHits.get()) / (double) total;
    }

    /**
     * 获取 L2 命中率
     */
    public double getL2HitRatio() {
        long total = totalRequests.get();
        if (total == 0) return 0.0;
        return l2Hits.get() / (double) total;
    }

    /**
     * 获取精确缓存统计信息
     */
    public CacheStats getExactCacheStats() {
        return exactCache.stats();
    }

    /**
     * 获取网格缓存统计信息
     */
    public CacheStats getGridCacheStats() {
        return gridCache.stats();
    }

    /**
     * 获取缓存大小信息
     */
    public CacheSizeInfo getSizeInfo() {
        return new CacheSizeInfo(
                exactCache.estimatedSize(),
                gridCache.estimatedSize(),
                exactCacheMaxSize,
                gridCacheMaxSize,
                redisEnabled
        );
    }

    /**
     * 获取详细统计信息
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
                totalRequests.get(),
                l1ExactHits.get(),
                l1GridHits.get(),
                l2Hits.get(),
                getHitRatio(),
                getL1HitRatio(),
                getL2HitRatio(),
                redisEnabled
        );
    }

    /**
     * 将缓存key转换为网格key
     * 假设key格式为: startLat_startLng_endLat_endLng_mode_...
     */
    private String toGridKey(String key) {
        try {
            String[] parts = key.split("_");
            if (parts.length < 5) return null;

            double startLat = Double.parseDouble(parts[0]);
            double startLng = Double.parseDouble(parts[1]);
            double endLat = Double.parseDouble(parts[2]);
            double endLng = Double.parseDouble(parts[3]);
            String mode = parts[4];

            // 将坐标量化到网格（约 gridSizeMeters 米精度）
            double gridDegrees = gridSizeMeters / 111320.0;

            long startGridLat = Math.round(startLat / gridDegrees);
            long startGridLng = Math.round(startLng / gridDegrees);
            long endGridLat = Math.round(endLat / gridDegrees);
            long endGridLng = Math.round(endLng / gridDegrees);

            return String.format("grid_%d_%d_%d_%d_%s", startGridLat, startGridLng, endGridLat, endGridLng, mode);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 缓存大小信息
     */
    public static class CacheSizeInfo {
        public final long exactSize;
        public final long gridSize;
        public final int exactMaxSize;
        public final int gridMaxSize;
        public final boolean redisEnabled;

        public CacheSizeInfo(long exactSize, long gridSize, int exactMaxSize, int gridMaxSize, boolean redisEnabled) {
            this.exactSize = exactSize;
            this.gridSize = gridSize;
            this.exactMaxSize = exactMaxSize;
            this.gridMaxSize = gridMaxSize;
            this.redisEnabled = redisEnabled;
        }
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStatistics {
        public final long totalRequests;
        public final long l1ExactHits;
        public final long l1GridHits;
        public final long l2Hits;
        public final double hitRatio;
        public final double l1HitRatio;
        public final double l2HitRatio;
        public final boolean redisEnabled;

        public CacheStatistics(long totalRequests, long l1ExactHits, long l1GridHits, long l2Hits,
                               double hitRatio, double l1HitRatio, double l2HitRatio, boolean redisEnabled) {
            this.totalRequests = totalRequests;
            this.l1ExactHits = l1ExactHits;
            this.l1GridHits = l1GridHits;
            this.l2Hits = l2Hits;
            this.hitRatio = hitRatio;
            this.l1HitRatio = l1HitRatio;
            this.l2HitRatio = l2HitRatio;
            this.redisEnabled = redisEnabled;
        }
    }
}
