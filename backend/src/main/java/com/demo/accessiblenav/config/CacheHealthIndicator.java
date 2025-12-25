package com.demo.accessiblenav.config;

import com.demo.accessiblenav.route.RouteCache;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 缓存健康检查 (支持两级缓存)
 */
@Component
public class CacheHealthIndicator implements HealthIndicator {

    private final RouteCache routeCache;

    public CacheHealthIndicator(RouteCache routeCache) {
        this.routeCache = routeCache;
    }

    @Override
    public Health health() {
        try {
            RouteCache.CacheSizeInfo sizeInfo = routeCache.getSizeInfo();
            RouteCache.CacheStatistics stats = routeCache.getStatistics();

            // 如果缓存使用率超过90%，标记为需要关注
            double exactUsageRatio = (double) sizeInfo.exactSize / sizeInfo.exactMaxSize;
            double gridUsageRatio = (double) sizeInfo.gridSize / sizeInfo.gridMaxSize;

            Health.Builder builder;
            if (exactUsageRatio > 0.95 || gridUsageRatio > 0.95) {
                builder = Health.status("DEGRADED")
                        .withDetail("warning", "L1缓存使用率过高");
            } else {
                builder = Health.up();
            }

            // L1 缓存信息
            builder.withDetail("l1_hitRatio", String.format("%.2f%%", stats.l1HitRatio * 100))
                    .withDetail("l1_exactCache", String.format("%d / %d (%.1f%%)",
                            sizeInfo.exactSize, sizeInfo.exactMaxSize, exactUsageRatio * 100))
                    .withDetail("l1_gridCache", String.format("%d / %d (%.1f%%)",
                            sizeInfo.gridSize, sizeInfo.gridMaxSize, gridUsageRatio * 100));

            // L2 Redis 信息
            builder.withDetail("l2_redisEnabled", sizeInfo.redisEnabled);
            if (sizeInfo.redisEnabled) {
                builder.withDetail("l2_hitRatio", String.format("%.2f%%", stats.l2HitRatio * 100));
            }

            // 总体统计
            builder.withDetail("totalHitRatio", String.format("%.2f%%", stats.hitRatio * 100))
                    .withDetail("totalRequests", stats.totalRequests)
                    .withDetail("l1ExactHits", stats.l1ExactHits)
                    .withDetail("l1GridHits", stats.l1GridHits)
                    .withDetail("l2Hits", stats.l2Hits);

            return builder.build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
