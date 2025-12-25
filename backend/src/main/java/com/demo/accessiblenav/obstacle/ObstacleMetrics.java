package com.demo.accessiblenav.obstacle;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Obstacle loop related metrics.
 *
 * Notes:
 * - Keep tags low-cardinality: status/type/event are enums/small sets.
 * - Prefer stable metric names (dot notation) for Micrometer.
 */
@Component
public class ObstacleMetrics {

    private final MeterRegistry registry;
    private final Timer reviewLatencyTimer;
    private final AtomicInteger dedupeEnabled = new AtomicInteger(1);

    public ObstacleMetrics(MeterRegistry registry,
                           ObstacleReportRepository reportRepository,
                           ObstacleEffectRepository effectRepository) {
        this.registry = registry;
        this.reviewLatencyTimer = Timer.builder("obstacle.review.latency")
                .description("Time from report creation to review decision")
                .publishPercentileHistogram()
                .register(registry);

        // Business gauges (low-cardinality, query-based).
        Gauge.builder("obstacle.reports.pending", reportRepository, r -> r.countByStatus(ObstacleReportService.STATUS_PENDING))
                .description("Number of pending obstacle reports")
                .strongReference(true)
                .register(registry);

        Gauge.builder("obstacle.effects.active", effectRepository, r -> r.countActiveDisabledNotExpired(Instant.now()))
                .description("Number of currently active disabled effects (not expired)")
                .strongReference(true)
                .register(registry);

        Gauge.builder("obstacle.dedupe.enabled", dedupeEnabled, AtomicInteger::doubleValue)
                .description("Whether obstacle report dedupe is enabled (1=true, 0=false)")
                .strongReference(true)
                .register(registry);
    }

    public void reportEvent(String event, String status, String type) {
        Counter.builder("obstacle.reports")
                .description("Obstacle report lifecycle events")
                .tag("event", safe(event))
                .tag("status", safe(status))
                .tag("type", safe(type))
                .register(registry)
                .increment();
    }

    public void recordReviewLatency(Duration duration) {
        if (duration == null) return;
        if (duration.isNegative()) return;
        reviewLatencyTimer.record(duration);
    }

    public void setDedupeEnabled(boolean enabled) {
        dedupeEnabled.set(enabled ? 1 : 0);
    }

    private static String safe(String v) {
        if (v == null) return "UNKNOWN";
        String s = v.trim();
        return s.isEmpty() ? "UNKNOWN" : s;
    }
}
