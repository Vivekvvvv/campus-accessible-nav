package com.demo.accessiblenav.obstacle;

import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.events.EventStreamService;
import com.demo.accessiblenav.obstacle.dto.EdgeDisableRequest;
import com.demo.accessiblenav.obstacle.dto.EdgeDisableResponse;
import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.route.GraphVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class ObstacleAdminService {

    private final EdgeRepository edgeRepository;
    private final ObstacleReportRepository reportRepository;
    private final ObstacleEffectRepository effectRepository;
    private final EventStreamService eventStreamService;
    private final GraphVersionService graphVersionService;
    private final OperationLogService logService;
    private final AdminPermissionService permissionService;
    private final ObstacleEffectManager effectManager;

    public ObstacleAdminService(EdgeRepository edgeRepository,
                               ObstacleReportRepository reportRepository,
                               ObstacleEffectRepository effectRepository,
                               EventStreamService eventStreamService,
                               GraphVersionService graphVersionService,
                               OperationLogService logService,
                               AdminPermissionService permissionService,
                               ObstacleEffectManager effectManager) {
        this.edgeRepository = edgeRepository;
        this.reportRepository = reportRepository;
        this.effectRepository = effectRepository;
        this.eventStreamService = eventStreamService;
        this.graphVersionService = graphVersionService;
        this.logService = logService;
        this.permissionService = permissionService;
        this.effectManager = effectManager;
    }

    @Transactional
    public EdgeDisableResponse disableEdge(EdgeDisableRequest req) {
        EdgeEntity edge = edgeRepository.findById(Objects.requireNonNull(req.getEdgeId()))
                .orElseThrow(() -> new IllegalArgumentException("edge not found"));

        ObstacleReportEntity report = new ObstacleReportEntity();
        report.setEdge(edge);
        report.setType("CONSTRUCTION");
        report.setStatus("APPROVED");
        report.setReason(req.getReason());
        report.setCreatedAt(Instant.now());
        report.setReviewedAt(Instant.now());
        report.setReviewerId(permissionService.currentUsername());
        report.setVerificationStatus("ADMIN_MANUAL");
        reportRepository.save(report);

        Instant startAt = Instant.now();
        String actor = permissionService.currentUsername();
        ObstacleEffectEntity effect = effectManager.applyDisableEffectWithHistory(
                report,
                edge,
                startAt,
                null,
                req.getReason(),
                actor
        );

        // 非单行边：同步禁用反向边（如果存在）
        if (!edge.isOneway()) {
            EdgeEntity rev = edgeRepository.findByFromNode_IdAndToNode_Id(edge.getToNode().getId(), edge.getFromNode().getId()).orElse(null);
            if (rev != null) {
                effectManager.applyDisableEffectWithHistory(
                        report,
                        rev,
                        startAt,
                        null,
                        req.getReason(),
                        actor
                );
            }
        }

        graphVersionService.bump();

        Map<String, Object> disabled = new HashMap<>();
        disabled.put("edgeId", edge.getId());
        disabled.put("reason", req.getReason());
        disabled.put("endAt", null);
        eventStreamService.publish("EDGE_DISABLED", disabled);
        logService.log("EDGE_DISABLED", "edgeId=" + edge.getId() + ", reason=" + req.getReason());

        return new EdgeDisableResponse(edge.getId(), true);
    }

    @Transactional
    public EdgeDisableResponse enableEdge(EdgeDisableRequest req) {
        EdgeEntity edge = edgeRepository.findById(Objects.requireNonNull(req.getEdgeId())).orElse(null);

        String actor = permissionService.currentUsername();
        Instant now = Instant.now();

        ObstacleEffectEntity effect = effectRepository.findByEdge_IdAndActiveTrue(req.getEdgeId()).orElse(null);
        if (effect != null) {
            effect.setActive(false);
            if (effect.getEndAt() == null || effect.getEndAt().isAfter(now)) {
                effect.setEndAt(now);
            }
            effect.setRevokedAt(now);
            effect.setRevokedBy(actor);
            effectRepository.save(effect);

            // Best-effort: update report status for UI/audit
            if (effect.getReport() != null && ObstacleReportService.STATUS_APPROVED.equals(effect.getReport().getStatus())) {
                effect.getReport().setStatus(ObstacleReportService.STATUS_REVOKED);
                reportRepository.save(effect.getReport());
            }
        }

        // 非单行边：同步解除反向边（如果存在）
        if (edge != null && !edge.isOneway()) {
            EdgeEntity rev = edgeRepository.findByFromNode_IdAndToNode_Id(edge.getToNode().getId(), edge.getFromNode().getId()).orElse(null);
            if (rev != null) {
                ObstacleEffectEntity revEffect = effectRepository.findByEdge_IdAndActiveTrue(rev.getId()).orElse(null);
                if (revEffect != null) {
                    revEffect.setActive(false);
                    if (revEffect.getEndAt() == null || revEffect.getEndAt().isAfter(now)) {
                        revEffect.setEndAt(now);
                    }
                    revEffect.setRevokedAt(now);
                    revEffect.setRevokedBy(actor);
                    effectRepository.save(revEffect);

                    if (revEffect.getReport() != null && ObstacleReportService.STATUS_APPROVED.equals(revEffect.getReport().getStatus())) {
                        revEffect.getReport().setStatus(ObstacleReportService.STATUS_REVOKED);
                        reportRepository.save(revEffect.getReport());
                    }
                }
            }
        }

        graphVersionService.bump();

        Map<String, Object> enabled = new HashMap<>();
        enabled.put("edgeId", req.getEdgeId());
        eventStreamService.publish("EDGE_ENABLED", enabled);
        logService.log("EDGE_ENABLED", "edgeId=" + req.getEdgeId());
        return new EdgeDisableResponse(req.getEdgeId(), false);
    }
}
