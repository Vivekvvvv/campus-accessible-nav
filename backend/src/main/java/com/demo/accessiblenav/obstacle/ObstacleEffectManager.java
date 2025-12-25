package com.demo.accessiblenav.obstacle;

import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.events.EventStreamService;
import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.route.GraphVersionService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Centralizes effect lifecycle operations so we can keep full history:
 * - When applying a new active effect for an edge, revoke any previous active effect (if different report),
 *   then INSERT a new row (do not overwrite) to preserve history.
 * - When revoking/expiring, flip active=false (keep the old row) and update report status.
 *
 * Note: DB side uniqueness is enforced via a Postgres partial unique index (active=true only).
 */
@Component
public class ObstacleEffectManager {

    private final ObstacleEffectRepository effectRepository;
    private final ObstacleReportRepository reportRepository;
    private final GraphVersionService graphVersionService;
    private final EventStreamService eventStreamService;
    private final OperationLogService logService;

    public ObstacleEffectManager(ObstacleEffectRepository effectRepository,
                                ObstacleReportRepository reportRepository,
                                GraphVersionService graphVersionService,
                                EventStreamService eventStreamService,
                                OperationLogService logService) {
        this.effectRepository = effectRepository;
        this.reportRepository = reportRepository;
        this.graphVersionService = graphVersionService;
        this.eventStreamService = eventStreamService;
        this.logService = logService;
    }

    @Transactional
    public ObstacleEffectEntity applyDisableEffectWithHistory(ObstacleReportEntity report,
                                                             EdgeEntity edge,
                                                             Instant startAt,
                                                             Instant endAt,
                                                             String reason,
                                                             String actor) {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(edge, "edge");
        Long edgeId = Objects.requireNonNull(edge.getId(), "edge.id");
        Instant now = startAt != null ? startAt : Instant.now();
        String who = (actor == null || actor.trim().isEmpty()) ? "SYSTEM" : actor.trim();

        ObstacleEffectEntity existing = effectRepository.findByEdge_IdAndActiveTrue(edgeId).orElse(null);
        if (existing != null) {
            // If it's already applied by the same report, treat as idempotent update.
            if (existing.getReport() != null && Objects.equals(existing.getReport().getId(), report.getId())) {
                existing.setDisabled(true);
                existing.setReason(reason);
                existing.setStartAt(now);
                existing.setEndAt(endAt);
                if (existing.getCreatedAt() == null) existing.setCreatedAt(now);
                if (existing.getCreatedBy() == null || existing.getCreatedBy().trim().isEmpty()) {
                    existing.setCreatedBy(who);
                }
                return effectRepository.save(existing);
            }

            revokeEffect(existing, now, who, true);
            // Ensure the active=true row is deactivated in DB before inserting a new active effect.
            // Otherwise Hibernate may flush INSERT before UPDATE and violate the partial unique index.
            effectRepository.flush();
        }

        ObstacleEffectEntity next = new ObstacleEffectEntity();
        next.setEdge(edge);
        next.setReport(report);
        next.setActive(true);
        next.setDisabled(true);
        next.setReason(reason);
        next.setStartAt(now);
        next.setEndAt(endAt);
        next.setCreatedAt(now);
        next.setCreatedBy(who);
        next.setTenantId(com.demo.accessiblenav.tenant.TenantContext.get());
        return effectRepository.save(next);
    }

    @Transactional
    public int revokeActiveEffectsByReport(Long reportId, Instant now, String actor) {
        Instant at = now != null ? now : Instant.now();
        String who = (actor == null || actor.trim().isEmpty()) ? "SYSTEM" : actor.trim();

        List<ObstacleEffectEntity> active = effectRepository.findAllByReport_IdAndActiveTrue(reportId);
        if (active.isEmpty()) {
            ObstacleReportEntity report = reportRepository.findById(reportId).orElse(null);
            if (report != null && ObstacleReportService.STATUS_APPROVED.equals(report.getStatus())) {
                report.setStatus(ObstacleReportService.STATUS_REVOKED);
                reportRepository.save(report);
                publishReportUpdated(report);
            }
            return 0;
        }

        for (ObstacleEffectEntity e : active) {
            revokeEffect(e, at, who, false);
        }

        ObstacleReportEntity report = active.get(0).getReport();
        if (report != null && ObstacleReportService.STATUS_APPROVED.equals(report.getStatus())) {
            report.setStatus(ObstacleReportService.STATUS_REVOKED);
            reportRepository.save(report);
            publishReportUpdated(report);
        }

        graphVersionService.bump();
        for (ObstacleEffectEntity e : active) {
            publishEdgeEnabled(e.getEdge() == null ? null : e.getEdge().getId());
        }
        logService.log("REPORT_REVOKED", "reportId=" + reportId + ", effects=" + active.size());
        return active.size();
    }

    @Transactional
    public int expireDueEffects(Instant now) {
        Instant at = now != null ? now : Instant.now();
        List<ObstacleEffectEntity> expired = effectRepository.findActiveExpiredEffects(at);
        if (expired.isEmpty()) return 0;

        for (ObstacleEffectEntity e : expired) {
            e.setActive(false);
            // Do not set revokedAt/revokedBy for auto-expire; endAt already captures the time boundary.
            effectRepository.save(e);

            ObstacleReportEntity report = e.getReport();
            if (report != null && ObstacleReportService.STATUS_APPROVED.equals(report.getStatus())) {
                report.setStatus(ObstacleReportService.STATUS_EXPIRED);
                reportRepository.save(report);
                publishReportUpdated(report);
            }
        }

        // Time-based effects can make route cache stale; callers may choose to bump. We keep it write-light here.
        logService.log("EFFECTS_EXPIRED", "count=" + expired.size());
        return expired.size();
    }

    private void revokeEffect(ObstacleEffectEntity e, Instant at, String actor, boolean overwrittenByNew) {
        if (e == null) return;
        e.setActive(false);
        if (e.getEndAt() == null || e.getEndAt().isAfter(at)) {
            e.setEndAt(at);
        }
        e.setRevokedAt(at);
        e.setRevokedBy(actor);
        effectRepository.save(e);

        ObstacleReportEntity prevReport = e.getReport();
        if (prevReport != null && ObstacleReportService.STATUS_APPROVED.equals(prevReport.getStatus())) {
            prevReport.setStatus(ObstacleReportService.STATUS_REVOKED);
            reportRepository.save(prevReport);
            publishReportUpdated(prevReport);
        }

        if (overwrittenByNew) {
            logService.log("EFFECT_REVOKED_OVERWRITTEN",
                    "effectId=" + e.getId() + ", edgeId=" + (e.getEdge() == null ? null : e.getEdge().getId()));
        } else {
            logService.log("EFFECT_REVOKED",
                    "effectId=" + e.getId() + ", edgeId=" + (e.getEdge() == null ? null : e.getEdge().getId()));
        }
    }

    private void publishEdgeEnabled(Long edgeId) {
        if (edgeId == null) return;
        Map<String, Object> enabled = new HashMap<>();
        enabled.put("edgeId", edgeId);
        eventStreamService.publish("EDGE_ENABLED", enabled);
    }

    private void publishReportUpdated(ObstacleReportEntity report) {
        if (report == null || report.getId() == null) return;
        Map<String, Object> updated = new HashMap<>();
        updated.put("reportId", report.getId());
        updated.put("edgeId", report.getEdge() == null ? null : report.getEdge().getId());
        updated.put("status", report.getStatus());
        eventStreamService.publish("REPORT_UPDATED", updated);
    }

    /**
     * NOTE: We intentionally keep the manager free of controller/security concerns.
     */
}
