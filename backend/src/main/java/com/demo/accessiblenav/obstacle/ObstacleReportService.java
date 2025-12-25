package com.demo.accessiblenav.obstacle;

import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.auth.UserAccount;
import com.demo.accessiblenav.auth.UserAccountService;
import com.demo.accessiblenav.auth.UserRole;
import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.events.EventStreamService;
import com.demo.accessiblenav.obstacle.dto.ObstacleReportCreateRequest;
import com.demo.accessiblenav.obstacle.dto.ObstacleReportDto;
import com.demo.accessiblenav.obstacle.dto.ObstacleReportReviewRequest;
import com.demo.accessiblenav.route.GraphRoutingService;
import com.demo.accessiblenav.route.GraphVersionService;
import com.demo.accessiblenav.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ObstacleReportService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_REVOKED = "REVOKED";

    private final EdgeRepository edgeRepository;
    private final ObstacleReportRepository reportRepository;
    private final ObstacleEffectRepository effectRepository;
    private final EventStreamService eventStreamService;
    private final GraphVersionService graphVersionService;
    private final OperationLogService logService;
    private final UserAccountService userAccountService;
    private final GraphRoutingService routingService;
    private final ObstacleMetrics obstacleMetrics;
    private final ObstacleEffectManager effectManager;
    private final DedupeKeyGenerator dedupeKeyGenerator;
    private final CredibilityScorer credibilityScorer;

    @Value("${app.obstacle.dedupe.enabled:true}")
    private boolean dedupeEnabled;

    @PostConstruct
    void initDedupeMetric() {
        obstacleMetrics.setDedupeEnabled(dedupeEnabled);
    }

    public ObstacleReportService(EdgeRepository edgeRepository,
                                ObstacleReportRepository reportRepository,
                                ObstacleEffectRepository effectRepository,
                                EventStreamService eventStreamService,
                                GraphVersionService graphVersionService,
                                OperationLogService logService,
                                UserAccountService userAccountService,
                                GraphRoutingService routingService,
                                ObstacleMetrics obstacleMetrics,
                                ObstacleEffectManager effectManager,
                                DedupeKeyGenerator dedupeKeyGenerator,
                                CredibilityScorer credibilityScorer) {
        this.edgeRepository = edgeRepository;
        this.reportRepository = reportRepository;
        this.effectRepository = effectRepository;
        this.eventStreamService = eventStreamService;
        this.graphVersionService = graphVersionService;
        this.logService = logService;
        this.userAccountService = userAccountService;
        this.routingService = routingService;
        this.obstacleMetrics = obstacleMetrics;
        this.effectManager = effectManager;
        this.dedupeKeyGenerator = dedupeKeyGenerator;
        this.credibilityScorer = credibilityScorer;
    }

    private String currentUsernameOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal == null) return null;
        String username = String.valueOf(principal);
        if (username == null) return null;
        username = username.trim();
        return username.isEmpty() ? null : username;
    }

    private void adjustSubmitterCreditIfEligible(ObstacleReportEntity report, int delta, String action) {
        if (report == null || delta == 0) return;
        String submitterId = report.getSubmitterId();
        if (submitterId == null || submitterId.trim().isEmpty()) return;

        UserAccount user = userAccountService.findByUsername(submitterId.trim());
        if (user == null) return;
        if (user.getRole() != UserRole.USER) return;

        userAccountService.applyCreditDeltaByUsername(user.getUsername(), delta);
        logService.log(
                "CREDIT_UPDATED",
                "action=" + action + ", reportId=" + report.getId() + ", submitter=" + user.getUsername() + ", delta=" + delta
        );
    }

    @Transactional
    public ObstacleReportDto createReport(ObstacleReportCreateRequest req) {
        obstacleMetrics.setDedupeEnabled(dedupeEnabled);

        Long edgeId = req.getEdgeId();
        if (edgeId == null) {
            if (req.getSubmitterLat() != null && req.getSubmitterLng() != null) {
                edgeId = routingService.findNearestEdgeId(req.getSubmitterLat(), req.getSubmitterLng());
            }
            if (edgeId == null) {
                throw new IllegalArgumentException("无法在指定位置附近找到路段，请确保位置准确");
            }
        }

        EdgeEntity edge = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new IllegalArgumentException("edge not found"));

        // PR-05: Duplicate report detection
        String dedupeKey = dedupeKeyGenerator.generate(edgeId, req.getType(), req.getSubmitterLat(), req.getSubmitterLng());
        if (dedupeEnabled) {
            List<ObstacleReportEntity> existing = reportRepository.findRecentByDedupeKeyAndTenant(
                    dedupeKey,
                    Instant.now().minus(24, ChronoUnit.HOURS),
                    TenantContext.get());
            if (!existing.isEmpty()) {
                ObstacleReportEntity primary = existing.get(0);
                primary.setConfirmCount(primary.getConfirmCount() + 1);
                reportRepository.save(primary);
                obstacleMetrics.reportEvent("deduplicated", primary.getStatus(), primary.getType());
                return toDto(primary);
            }
        }

        ObstacleReportEntity report = new ObstacleReportEntity();
        report.setEdge(edge);
        report.setType(req.getType());
        // v2: default to PENDING; admin decides whether to apply an effect.
        report.setStatus(STATUS_PENDING);
        report.setReason(req.getReason());
        report.setCreatedAt(Instant.now());

        // 设置租户
        report.setTenantId(com.demo.accessiblenav.tenant.TenantContext.get());

        // 设置照片 URL（多张照片用逗号分隔）
        if (req.getPhotoUrls() != null && !req.getPhotoUrls().isEmpty()) {
            String photoUrlsCombined = String.join(",", req.getPhotoUrls());
            if (photoUrlsCombined.length() > 2000) {
                photoUrlsCombined = photoUrlsCombined.substring(0, 2000);
            }
            report.setPhotoUrls(photoUrlsCombined);
        }

        // 设置提交者 GPS 位置
        if (req.getSubmitterLat() != null && req.getSubmitterLng() != null) {
            report.setSubmitterLat(req.getSubmitterLat());
            report.setSubmitterLng(req.getSubmitterLng());
        }

        // 设置提交者信息（从请求中获取，或从认证上下文获取）
        if (req.getSubmitterName() != null && !req.getSubmitterName().trim().isEmpty()) {
            report.setSubmitterName(req.getSubmitterName().trim());
        }

        // submitterId：仅当用户携带 Bearer token 时才会有（游客上报则为空）
        String submitterUsername = currentUsernameOrNull();
        if (submitterUsername != null) {
            report.setSubmitterId(submitterUsername);
            // 如果未填“称呼”，默认用用户名展示（更省事）
            if (report.getSubmitterName() == null || report.getSubmitterName().trim().isEmpty()) {
                report.setSubmitterName(submitterUsername);
            }
        }

        // 初始化核查状态为未核查
        report.setVerificationStatus("UNVERIFIED");

        // PR-05: Set dedupe key
        report.setDedupeKey(dedupeKey);

        reportRepository.save(report);

        // Compute and persist confidence score
        report.setConfidenceScore(credibilityScorer.computeConfidence(report));
        reportRepository.save(report);

        Map<String, Object> created = new HashMap<>();
        created.put("reportId", report.getId());
        created.put("edgeId", report.getEdge().getId());
        created.put("status", report.getStatus());
        eventStreamService.publish("REPORT_CREATED", created);
        logService.log("REPORT_CREATED", "reportId=" + report.getId() + ", edgeId=" + report.getEdge().getId());
        obstacleMetrics.reportEvent("created", report.getStatus(), report.getType());
        return toDto(report);
    }

    @Transactional
    public List<ObstacleReportDto> listReports(String status) {
        // Ensure auto-expired effects and report statuses are reconciled before listing (best-effort).
        effectManager.expireDueEffects(Instant.now());
        String st = (status == null || status.trim().isEmpty()) ? STATUS_PENDING : status.trim();
        return reportRepository.findByStatusAndTenantIdOrderByCreatedAtDesc(st, TenantContext.get())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<ObstacleReportDto> listMyReports(String status) {
        String submitter = currentUsernameOrNull();
        if (submitter == null) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }

        // Ensure the user sees latest EXPIRED status if any of their approved effects elapsed.
        effectManager.expireDueEffects(Instant.now());

        if (status == null || status.trim().isEmpty()) {
            return reportRepository.findBySubmitterIdAndTenantIdOrderByCreatedAtDesc(submitter, TenantContext.get())
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
        }

        String st = status.trim();
        return reportRepository.findBySubmitterIdAndStatusAndTenantIdOrderByCreatedAtDesc(submitter, st, TenantContext.get())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ObstacleReportDto approve(Long reportId, ObstacleReportReviewRequest req) {
        ObstacleReportEntity report = reportRepository.findById(Objects.requireNonNull(reportId))
                .orElseThrow(() -> new IllegalArgumentException("report not found"));
        if (!TenantContext.get().equals(report.getTenantId())) {
            throw new IllegalArgumentException("report not found");
        }

        String previousStatus = report.getStatus();

        report.setStatus(STATUS_APPROVED);
        if (req != null && req.getReason() != null && !req.getReason().trim().isEmpty()) {
            report.setReason(req.getReason().trim());
        }

        // 设置审核信息（即使 req 为空也要记录 reviewer/reviewedAt）
        String reviewer = currentUsernameOrNull();
        report.setReviewerId(reviewer != null ? reviewer : "SYSTEM");

        // 审核备注
        if (req != null && req.getReviewNote() != null && !req.getReviewNote().trim().isEmpty()) {
            String note = req.getReviewNote().trim();
            if (note.length() > 1000) {
                note = note.substring(0, 1000);
            }
            report.setReviewNote(note);
        }

        // 核查状态（审核通过时通常设为 VERIFIED）
        if (req != null && req.getVerificationStatus() != null && !req.getVerificationStatus().trim().isEmpty()) {
            report.setVerificationStatus(req.getVerificationStatus().trim());
        } else {
            report.setVerificationStatus("VERIFIED");
        }

        report.setReviewedAt(Instant.now());

        reportRepository.save(report);

        // 仅在 PENDING -> APPROVED 时加分，避免重复调用产生重复加分
        if (STATUS_PENDING.equals(previousStatus) && STATUS_APPROVED.equals(report.getStatus())) {
            adjustSubmitterCreditIfEligible(report, +5, "REPORT_APPROVED");
        }

        Instant startAt = Instant.now();
        Instant endAt = null;
        if (req != null && req.getDurationMinutes() != null && req.getDurationMinutes() > 0) {
            endAt = startAt.plus(req.getDurationMinutes(), ChronoUnit.MINUTES);
        }

        // 审核通过 -> 生效：禁用该边（保留历史：撤销旧 effect，插入新 effect）
        ObstacleEffectEntity effect = effectManager.applyDisableEffectWithHistory(
                report,
                report.getEdge(),
                startAt,
                endAt,
                report.getReason(),
                report.getReviewerId()
        );

        // 非单行边：同步禁用反向边（如果存在）
        EdgeEntity edge = report.getEdge();
        if (edge != null && !edge.isOneway()) {
            EdgeEntity rev = edgeRepository.findByFromNode_IdAndToNode_Id(edge.getToNode().getId(), edge.getFromNode().getId()).orElse(null);
            if (rev != null) {
                effectManager.applyDisableEffectWithHistory(
                        report,
                        rev,
                        startAt,
                        endAt,
                        effect.getReason(),
                        report.getReviewerId()
                );
            }
        }

        graphVersionService.bump();

        Map<String, Object> updated = new HashMap<>();
        updated.put("reportId", report.getId());
        updated.put("edgeId", report.getEdge().getId());
        updated.put("status", report.getStatus());
        eventStreamService.publish("REPORT_UPDATED", updated);

        Map<String, Object> disabled = new HashMap<>();
        disabled.put("edgeId", report.getEdge().getId());
        disabled.put("reason", effect.getReason());
        disabled.put("endAt", effect.getEndAt());
        eventStreamService.publish("EDGE_DISABLED", disabled);
        logService.log("REPORT_APPROVED", "reportId=" + report.getId() + ", edgeId=" + report.getEdge().getId());
        if (!Objects.equals(previousStatus, report.getStatus())) {
            obstacleMetrics.reportEvent("approved", report.getStatus(), report.getType());
            if (report.getCreatedAt() != null && report.getReviewedAt() != null) {
                obstacleMetrics.recordReviewLatency(Duration.between(report.getCreatedAt(), report.getReviewedAt()));
            }
        }
        return toDto(report);
    }

    @Transactional
    public ObstacleReportDto reject(Long reportId, ObstacleReportReviewRequest req) {
        ObstacleReportEntity report = reportRepository.findById(Objects.requireNonNull(reportId))
                .orElseThrow(() -> new IllegalArgumentException("report not found"));
        if (!TenantContext.get().equals(report.getTenantId())) {
            throw new IllegalArgumentException("report not found");
        }

        String previousStatus = report.getStatus();

        report.setStatus(STATUS_REJECTED);
        if (req != null && req.getReason() != null && !req.getReason().trim().isEmpty()) {
            report.setReason(req.getReason().trim());
        }

        // 设置审核信息（即使 req 为空也要记录 reviewer/reviewedAt）
        String reviewer = currentUsernameOrNull();
        report.setReviewerId(reviewer != null ? reviewer : "SYSTEM");

        // 审核备注
        if (req != null && req.getReviewNote() != null && !req.getReviewNote().trim().isEmpty()) {
            String note = req.getReviewNote().trim();
            if (note.length() > 1000) {
                note = note.substring(0, 1000);
            }
            report.setReviewNote(note);
        }

        // 核查状态（拒绝时通常也是已核查的）
        if (req != null && req.getVerificationStatus() != null && !req.getVerificationStatus().trim().isEmpty()) {
            report.setVerificationStatus(req.getVerificationStatus().trim());
        } else {
            report.setVerificationStatus("VERIFIED");
        }

        report.setReviewedAt(Instant.now());

        reportRepository.save(report);

        // 仅在 PENDING -> REJECTED 时扣分，避免重复调用产生重复扣分
        if (STATUS_PENDING.equals(previousStatus) && STATUS_REJECTED.equals(report.getStatus())) {
            adjustSubmitterCreditIfEligible(report, -3, "REPORT_REJECTED");
        }

        Map<String, Object> updated = new HashMap<>();
        updated.put("reportId", report.getId());
        updated.put("edgeId", report.getEdge().getId());
        updated.put("status", report.getStatus());
        eventStreamService.publish("REPORT_UPDATED", updated);
        logService.log("REPORT_REJECTED", "reportId=" + report.getId() + ", edgeId=" + report.getEdge().getId());
        if (!Objects.equals(previousStatus, report.getStatus())) {
            obstacleMetrics.reportEvent("rejected", report.getStatus(), report.getType());
            if (report.getCreatedAt() != null && report.getReviewedAt() != null) {
                obstacleMetrics.recordReviewLatency(Duration.between(report.getCreatedAt(), report.getReviewedAt()));
            }
        }
        return toDto(report);
    }

    @Transactional
    public ObstacleReportDto revoke(Long reportId, String note) {
        ObstacleReportEntity report = reportRepository.findById(Objects.requireNonNull(reportId))
                .orElseThrow(() -> new IllegalArgumentException("report not found"));
        if (!TenantContext.get().equals(report.getTenantId())) {
            throw new IllegalArgumentException("report not found");
        }

        // Best-effort reconcile any due expirations first; if it's already expired, keep it expired.
        effectManager.expireDueEffects(Instant.now());

        if (STATUS_PENDING.equals(report.getStatus())) {
            throw new IllegalStateException("cannot revoke a pending report");
        }
        if (STATUS_REJECTED.equals(report.getStatus())) {
            throw new IllegalStateException("cannot revoke a rejected report");
        }
        if (STATUS_EXPIRED.equals(report.getStatus()) || STATUS_REVOKED.equals(report.getStatus())) {
            return toDto(report);
        }

        String actor = currentUsernameOrNull();
        if (actor == null) actor = "SYSTEM";

        // Optional note: append a revoke marker for traceability (bounded).
        if (note != null && !note.trim().isEmpty()) {
            String n = note.trim();
            String existing = report.getReviewNote();
            String merged = (existing == null || existing.trim().isEmpty()) ? n : (existing + "\nREVOKE: " + n);
            if (merged.length() > 1000) merged = merged.substring(0, 1000);
            report.setReviewNote(merged);
        }

        effectManager.revokeActiveEffectsByReport(reportId, Instant.now(), actor);
        // effectManager already updates report status and bumps graph version when needed.

        ObstacleReportEntity updated = reportRepository.findById(reportId).orElse(report);
        obstacleMetrics.reportEvent("revoked", updated.getStatus(), updated.getType());
        return toDto(updated);
    }

    private ObstacleReportDto toDto(ObstacleReportEntity e) {
        ObstacleReportDto dto = new ObstacleReportDto(
                e.getId(),
                e.getEdge() == null ? null : e.getEdge().getId(),
                e.getType(),
                e.getStatus(),
                e.getReason(),
                e.getCreatedAt()
        );

        // 设置照片 URL 列表
        if (e.getPhotoUrls() != null && !e.getPhotoUrls().isEmpty()) {
            String[] urls = e.getPhotoUrls().split(",");
            dto.setPhotoUrls(java.util.Arrays.asList(urls));
        }

        // 设置提交者位置
        dto.setSubmitterLat(e.getSubmitterLat());
        dto.setSubmitterLng(e.getSubmitterLng());
        dto.setSubmitterName(e.getSubmitterName());

        // 设置审核信息
        dto.setReviewNote(e.getReviewNote());
        dto.setReviewedAt(e.getReviewedAt());
        dto.setVerificationStatus(e.getVerificationStatus());

        // 设置确认次数
        dto.setConfirmCount(e.getConfirmCount());

        // 设置可信度评分
        dto.setConfidenceScore(e.getConfidenceScore());

        // 设置升级状态
        dto.setEscalated(e.isEscalated());
        dto.setEscalatedAt(e.getEscalatedAt());

        return dto;
    }
}
