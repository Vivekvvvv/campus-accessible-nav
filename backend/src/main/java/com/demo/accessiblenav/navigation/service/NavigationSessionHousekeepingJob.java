package com.demo.accessiblenav.navigation.service;

import com.demo.accessiblenav.navigation.persistence.NavigationSessionEntity;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionEventEntity;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionEventRepository;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionRepository;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Ends navigation sessions that are stale or exceed max duration.
 *
 * This provides a server-side safety net so sessions don't remain ACTIVE forever
 * if clients stop reporting location or crash.
 */
@Service
public class NavigationSessionHousekeepingJob {

    private static final Logger log = LoggerFactory.getLogger(NavigationSessionHousekeepingJob.class);

    private final NavigationSessionRepository sessionRepository;
    private final NavigationSessionEventRepository eventRepository;
    private final NavigationMetrics metrics;
    private final NavigationHousekeepingProperties props;

    public NavigationSessionHousekeepingJob(NavigationSessionRepository sessionRepository,
                                           NavigationSessionEventRepository eventRepository,
                                           NavigationMetrics metrics,
                                           NavigationHousekeepingProperties props) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.metrics = metrics;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.navigation.housekeeping.fixed-delay-ms:60000}")
    public void scheduledSweep() {
        // Keep scheduled method thin; run transactional work in sweepOnce().
        sweepOnce();
    }

    @Transactional
    public void sweepOnce() {
        if (!props.isEnabled()) return;

        Instant now = Instant.now();
        Duration maxDur = safeDuration(props.getMaxDuration(), Duration.ofHours(4));
        Duration staleDur = safeDuration(props.getStaleLocation(), Duration.ofMinutes(10));
        int batch = Math.max(1, Math.min(2000, props.getBatchSize()));

        List<NavigationSessionStatus> activeStatuses = List.of(NavigationSessionStatus.ACTIVE, NavigationSessionStatus.PAUSED);

        // 1) Max duration cutoff
        Instant maxCutoff = now.minus(maxDur);
        List<NavigationSessionEntity> timedOut = sessionRepository.findByStatusInAndStartedAtBeforeOrderByStartedAtAsc(
                activeStatuses, maxCutoff, PageRequest.of(0, batch)
        );
        int endedTimeout = endSessions(now, timedOut, "TIMEOUT");

        // 2) Stale location cutoff (only applies when last_location_at exists)
        Instant staleCutoff = now.minus(staleDur);
        List<NavigationSessionEntity> stale = sessionRepository.findByStatusInAndLastLocationAtBeforeOrderByLastLocationAtAsc(
                activeStatuses, staleCutoff, PageRequest.of(0, batch)
        );
        int endedStale = endSessions(now, stale, "STALE_LOCATION");

        if (endedTimeout + endedStale > 0) {
            log.info("navigation housekeeping ended sessions: timeout={}, stale={}", endedTimeout, endedStale);
        }
    }

    private int endSessions(Instant now, List<NavigationSessionEntity> sessions, String reason) {
        int ended = 0;
        for (NavigationSessionEntity s : sessions) {
            if (s == null) continue;
            if (s.getStatus() == NavigationSessionStatus.ENDED) continue;

            s.setStatus(NavigationSessionStatus.ENDED);
            s.setEndedAt(now);
            sessionRepository.save(s);

            NavigationSessionEventEntity e = new NavigationSessionEventEntity();
            e.setSession(s);
            e.setEventType(NavigationSessionEventType.END);
            e.setCreatedAt(now);
            e.setPayloadJson("{\"reason\":\"" + safeJson(reason) + "\"}");
            eventRepository.save(e);

            metrics.incEnded(reason);
            if (s.getStartedAt() != null) {
                double seconds = Duration.between(s.getStartedAt(), now).toMillis() / 1000.0;
                metrics.recordSessionDurationSeconds(seconds);
            }
            ended++;
        }
        return ended;
    }

    private static Duration safeDuration(Duration v, Duration fallback) {
        if (v == null) return fallback;
        if (v.isNegative() || v.isZero()) return fallback;
        return v;
    }

    private static String safeJson(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

