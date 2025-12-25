package com.demo.accessiblenav.obstacle;

import com.demo.accessiblenav.events.EventStreamService;
import com.demo.accessiblenav.tenant.TenantContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Periodically checks for PENDING reports that exceed the SLA threshold
 * and marks them as escalated.
 */
@Component
public class ReviewSlaScheduler {

    private static final long SLA_THRESHOLD_MINUTES = 30;

    private final ObstacleReportRepository reportRepository;
    private final EventStreamService eventStreamService;

    public ReviewSlaScheduler(ObstacleReportRepository reportRepository,
                              EventStreamService eventStreamService) {
        this.reportRepository = reportRepository;
        this.eventStreamService = eventStreamService;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void checkSla() {
        Instant threshold = Instant.now().minus(SLA_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        List<ObstacleReportEntity> overdue = reportRepository.findPendingCreatedBefore(threshold);
        Set<String> tenantIds = overdue.stream()
                .map(ObstacleReportEntity::getTenantId)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.toSet());
        for (String tenantId : tenantIds) {
            TenantContext.set(tenantId);
            try {
                List<ObstacleReportEntity> tenantOverdue = reportRepository.findPendingCreatedBeforeAndTenant(threshold, tenantId);
                for (ObstacleReportEntity report : tenantOverdue) {
                    report.setEscalated(true);
                    report.setEscalatedAt(Instant.now());
                    reportRepository.save(report);

                    Map<String, Object> event = new HashMap<>();
                    event.put("reportId", report.getId());
                    event.put("edgeId", report.getEdge().getId());
                    event.put("tenantId", tenantId);
                    event.put("escalatedAt", report.getEscalatedAt());
                    eventStreamService.publish("REPORT_ESCALATED", event);
                }
            } finally {
                TenantContext.clear();
            }
        }
    }
}
