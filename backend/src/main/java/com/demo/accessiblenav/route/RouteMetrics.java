package com.demo.accessiblenav.route;

import com.demo.accessiblenav.exception.BusinessException;
import com.demo.accessiblenav.route.dto.RouteStrategy;
import com.demo.accessiblenav.route.dto.TravelMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Route-related metrics (stage timing + failure reasons).
 *
 * Notes:
 * - Keep tags low-cardinality: stage/reason/mode/strategy are small sets.
 * - Use dot-notation metric names in Micrometer; Prometheus exporter will convert to underscores.
 */
@Component
public class RouteMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Timer> stageTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<RequestKey, Counter> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<FailureKey, Counter> failureCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> calculationErrorCounters = new ConcurrentHashMap<>();

    public RouteMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T recordStage(String stage, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return supplier.get();
        } finally {
            sample.stop(stageTimer(stage));
        }
    }

    public void incRequest(boolean cacheHit,
                           boolean success,
                           TravelMode mode,
                           RouteStrategy strategy,
                           boolean campusOnly) {
        RequestKey key = new RequestKey(cacheHit, success, mode, strategy, campusOnly);
        requestCounters.computeIfAbsent(key, k ->
                Counter.builder("route.requests")
                        .description("Route requests by result/cache/mode/strategy")
                        .tag("cache", k.cacheHit ? "hit" : "miss")
                        .tag("result", k.success ? "success" : "failure")
                        .tag("mode", safe(k.mode != null ? k.mode.name() : null))
                        .tag("strategy", safe(k.strategy != null ? k.strategy.name() : null))
                        .tag("campus_only", k.campusOnly ? "true" : "false")
                        .register(registry)
        ).increment();
    }

    public void incFailure(String reasonCode,
                           String stage,
                           TravelMode mode,
                           RouteStrategy strategy,
                           boolean campusOnly) {
        incCalculationError(reasonCode);
        FailureKey key = new FailureKey(reasonCode, stage, mode, strategy, campusOnly);
        failureCounters.computeIfAbsent(key, k ->
                Counter.builder("route.failures")
                        .description("Route failures by reason/stage/mode/strategy")
                        .tag("reason", safe(k.reasonCode))
                        .tag("stage", safe(k.stage))
                        .tag("mode", safe(k.mode != null ? k.mode.name() : null))
                        .tag("strategy", safe(k.strategy != null ? k.strategy.name() : null))
                        .tag("campus_only", k.campusOnly ? "true" : "false")
                        .register(registry)
        ).increment();
    }

    public void incCalculationError(String reasonCode) {
        String reason = safe(reasonCode);
        calculationErrorCounters.computeIfAbsent(reason, r ->
                Counter.builder("route.calculation.errors")
                        .description("Route calculation error count by reason")
                        .tag("reason", r)
                        .register(registry)
        ).increment();
    }

    public String classifyFailureCode(Throwable ex) {
        if (ex == null) return "UNKNOWN";
        if (ex instanceof BusinessException) {
            return ((BusinessException) ex).getCode();
        }
        // Align with ApiExceptionHandler defaults for non-business exceptions.
        if (ex instanceof IllegalArgumentException) return "INVALID_ARGUMENT";
        if (ex instanceof IllegalStateException) return "INVALID_STATE";
        return "INTERNAL_ERROR";
    }

    private Timer stageTimer(String stage) {
        String s = safe(stage);
        return stageTimers.computeIfAbsent(s, k ->
                Timer.builder("route.stage.duration")
                        .description("Route stage duration")
                        .tag("stage", k)
                        .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                        .publishPercentileHistogram()
                        .register(registry)
        );
    }

    private static String safe(String v) {
        if (v == null) return "UNKNOWN";
        String s = v.trim();
        return s.isEmpty() ? "UNKNOWN" : s;
    }

    private static final class RequestKey {
        final boolean cacheHit;
        final boolean success;
        final TravelMode mode;
        final RouteStrategy strategy;
        final boolean campusOnly;

        RequestKey(boolean cacheHit, boolean success, TravelMode mode, RouteStrategy strategy, boolean campusOnly) {
            this.cacheHit = cacheHit;
            this.success = success;
            this.mode = mode;
            this.strategy = strategy;
            this.campusOnly = campusOnly;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RequestKey)) return false;
            RequestKey that = (RequestKey) o;
            return cacheHit == that.cacheHit
                    && success == that.success
                    && campusOnly == that.campusOnly
                    && mode == that.mode
                    && strategy == that.strategy;
        }

        @Override
        public int hashCode() {
            int result = (cacheHit ? 1 : 0);
            result = 31 * result + (success ? 1 : 0);
            result = 31 * result + (campusOnly ? 1 : 0);
            result = 31 * result + (mode != null ? mode.hashCode() : 0);
            result = 31 * result + (strategy != null ? strategy.hashCode() : 0);
            return result;
        }
    }

    private static final class FailureKey {
        final String reasonCode;
        final String stage;
        final TravelMode mode;
        final RouteStrategy strategy;
        final boolean campusOnly;

        FailureKey(String reasonCode, String stage, TravelMode mode, RouteStrategy strategy, boolean campusOnly) {
            this.reasonCode = reasonCode;
            this.stage = stage;
            this.mode = mode;
            this.strategy = strategy;
            this.campusOnly = campusOnly;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FailureKey)) return false;
            FailureKey that = (FailureKey) o;
            return campusOnly == that.campusOnly
                    && Objects.equals(reasonCode, that.reasonCode)
                    && Objects.equals(stage, that.stage)
                    && mode == that.mode
                    && strategy == that.strategy;
        }

        @Override
        public int hashCode() {
            int result = (reasonCode != null ? reasonCode.hashCode() : 0);
            result = 31 * result + (stage != null ? stage.hashCode() : 0);
            result = 31 * result + (mode != null ? mode.hashCode() : 0);
            result = 31 * result + (strategy != null ? strategy.hashCode() : 0);
            result = 31 * result + (campusOnly ? 1 : 0);
            return result;
        }
    }
}
