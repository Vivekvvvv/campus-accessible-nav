package com.demo.accessiblenav.config;

import com.demo.accessiblenav.route.RouteCache;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 性能监控指标配置
 * 暴露路由缓存、JVM 等自定义指标给 Prometheus/Actuator
 */
@Configuration
public class MetricsConfig {

    private final RouteCache routeCache;

    public MetricsConfig(RouteCache routeCache) {
        this.routeCache = routeCache;
    }

    @Bean
    public MeterBinder routeCacheMetrics() {
        return registry -> {
            // 总体命中率
            Gauge.builder("route.cache.hit_ratio", routeCache, RouteCache::getHitRatio)
                    .description("路由缓存总命中率")
                    .register(registry);

            // L1/L2 分层命中率
            Gauge.builder("route.cache.l1_hit_ratio", routeCache, RouteCache::getL1HitRatio)
                    .description("L1 Caffeine 命中率")
                    .register(registry);

            Gauge.builder("route.cache.l2_hit_ratio", routeCache, RouteCache::getL2HitRatio)
                    .description("L2 Redis 命中率")
                    .register(registry);

            // 缓存大小
            Gauge.builder("route.cache.exact.size", routeCache,
                    cache -> cache.getSizeInfo().exactSize)
                    .description("精确缓存当前大小")
                    .register(registry);

            Gauge.builder("route.cache.grid.size", routeCache,
                    cache -> cache.getSizeInfo().gridSize)
                    .description("网格缓存当前大小")
                    .register(registry);

            Gauge.builder("route.cache.exact.max_size", routeCache,
                    cache -> cache.getSizeInfo().exactMaxSize)
                    .description("精确缓存最大容量")
                    .register(registry);

            Gauge.builder("route.cache.grid.max_size", routeCache,
                    cache -> cache.getSizeInfo().gridMaxSize)
                    .description("网格缓存最大容量")
                    .register(registry);

            // 请求统计
            Gauge.builder("route.cache.total_requests", routeCache,
                    cache -> cache.getStatistics().totalRequests)
                    .description("缓存总请求数")
                    .register(registry);

            Gauge.builder("route.cache.l1_exact_hits", routeCache,
                    cache -> cache.getStatistics().l1ExactHits)
                    .description("L1 精确命中数")
                    .register(registry);

            Gauge.builder("route.cache.l1_grid_hits", routeCache,
                    cache -> cache.getStatistics().l1GridHits)
                    .description("L1 网格命中数")
                    .register(registry);

            Gauge.builder("route.cache.l2_hits", routeCache,
                    cache -> cache.getStatistics().l2Hits)
                    .description("L2 Redis 命中数")
                    .register(registry);
        };
    }

    /**
     * 路由计算计时器
     */
    @Bean
    public Timer routeCalculationTimer(MeterRegistry registry) {
        return Timer.builder("route.calculation.duration")
                .description("路由计算耗时")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                // Export histogram buckets for PromQL histogram_quantile() and alerting.
                .publishPercentileHistogram()
                .register(registry);
    }

    /**
     * JVM 内存使用率指标
     */
    @Bean
    public MeterBinder jvmMemoryUsagePercentage() {
        return registry -> {
            Gauge.builder("jvm.memory.usage_percent", () -> {
                        Runtime rt = Runtime.getRuntime();
                        long total = rt.totalMemory();
                        long free = rt.freeMemory();
                        return total > 0 ? (double) (total - free) / total * 100.0 : 0.0;
                    })
                    .description("JVM 内存使用率百分比")
                    .baseUnit("percent")
                    .register(registry);
        };
    }
}
