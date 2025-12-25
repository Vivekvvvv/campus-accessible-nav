package com.demo.accessiblenav.navigation.service;

import com.demo.accessiblenav.navigation.persistence.NavigationSessionRepository;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class NavigationMetrics {

    private final MeterRegistry registry;
    private final NavigationSessionRepository sessionRepository;

    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final Timer sessionDurationTimer;

    public NavigationMetrics(MeterRegistry registry, NavigationSessionRepository sessionRepository) {
        this.registry = registry;
        this.sessionRepository = sessionRepository;

        Gauge.builder("navigation.sessions.active", sessionRepository,
                        repo -> repo.countByStatus(NavigationSessionStatus.ACTIVE))
                .description("Active navigation sessions")
                .register(registry);

        this.sessionDurationTimer = Timer.builder("navigation.session.duration")
                .description("Navigation session duration (seconds)")
                .publishPercentileHistogram()
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(registry);
    }

    public void incStarted(String mode) {
        counter("navigation.sessions.started", "mode", safe(mode)).increment();
    }

    public void incEnded(String reason) {
        counter("navigation.sessions.ended", "reason", safe(reason)).increment();
    }

    public void incReroute(String mode, String reason) {
        counter("navigation.reroutes", "mode", safe(mode), "reason", safe(reason)).increment();
    }

    public void incLocationUpdates() {
        counter("navigation.location.updates").increment();
    }

    public void incHazardsQuery() {
        counter("navigation.hazards.queries").increment();
    }

    public void addHazardsMatched(int count) {
        if (count <= 0) return;
        counter("navigation.hazards.matched").increment(count);
    }

    public void incClientEvent(String type) {
        counter("navigation.client.events", "type", safe(type)).increment();
    }

    public void recordSessionDurationSeconds(double seconds) {
        if (seconds < 0) return;
        sessionDurationTimer.record((long) (seconds * 1000), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private Counter counter(String name, String... tags) {
        String key = name + "|" + String.join("|", tags);
        return counters.computeIfAbsent(key, k ->
                Counter.builder(name)
                        .description(name)
                        .tags(tags)
                        .register(registry)
        );
    }

    private static String safe(String v) {
        if (v == null) return "UNKNOWN";
        String s = v.trim();
        return s.isEmpty() ? "UNKNOWN" : s;
    }
}
