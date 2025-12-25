package com.demo.accessiblenav.graph.change;

import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.auth.UserRole;
import com.demo.accessiblenav.graph.GraphImportService;
import com.demo.accessiblenav.graph.dto.GraphChangePayload;
import com.demo.accessiblenav.graph.dto.GraphChangeRequestCreateRequest;
import com.demo.accessiblenav.graph.dto.GraphChangeRequestDetailDto;
import com.demo.accessiblenav.graph.dto.GraphChangeRequestDto;
import com.demo.accessiblenav.graph.dto.GraphChangeRequestReviewRequest;
import com.demo.accessiblenav.graph.dto.GraphImportRequest;
import com.demo.accessiblenav.graph.dto.GraphReplaceRequest;
import com.demo.accessiblenav.graph.dto.GraphValidationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class GraphChangeRequestService {

    private final GraphChangeRequestRepository repository;
    private final GraphImportService graphImportService;
    private final AdminPermissionService permissionService;
    private final OperationLogService logService;
    private final ObjectMapper objectMapper;

    public GraphChangeRequestService(GraphChangeRequestRepository repository,
                                     GraphImportService graphImportService,
                                     AdminPermissionService permissionService,
                                     OperationLogService logService,
                                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.graphImportService = graphImportService;
        this.permissionService = permissionService;
        this.logService = logService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GraphChangeRequestDto submit(GraphChangeRequestCreateRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.EDITOR);
        GraphChangePayload payload = req.getPayload();
        ensurePayloadNotEmpty(payload);

        GraphImportRequest importRequest = toImportRequest(payload);
        GraphValidationReport report = graphImportService.validatePayload(importRequest, true);

        GraphChangeRequestEntity entity = new GraphChangeRequestEntity();
        entity.setKind(req.getKind());
        entity.setPayloadType(req.getPayloadType());
        entity.setStatus(GraphChangeStatus.SUBMITTED);
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(permissionService.currentUsername());
        entity.setSubmitNote(trim(req.getNote()));
        entity.setNodeCount(report.getNodeCount());
        entity.setEdgeCount(report.getEdgeCount());
        entity.setQualityScore(report.getQualityScore());
        try {
            entity.setPayloadJson(objectMapper.writeValueAsString(payload));
            entity.setReportJson(objectMapper.writeValueAsString(report));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload serialize failed", e);
        }

        GraphChangeRequestEntity saved = repository.save(entity);
        logService.log("GRAPH_CHANGE_SUBMIT", "id=" + saved.getId() + ", kind=" + saved.getKind() + ", type=" + saved.getPayloadType());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<GraphChangeRequestDto> list(String status, String kind) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER, UserRole.EDITOR, UserRole.VIEWER);
        GraphChangeStatus statusEnum = parseStatus(status);
        GraphChangeKind kindEnum = parseKind(kind);
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(e -> statusEnum == null || e.getStatus() == statusEnum)
                .filter(e -> kindEnum == null || e.getKind() == kindEnum)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GraphChangeRequestDetailDto detail(Long id) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER, UserRole.EDITOR, UserRole.VIEWER);
        GraphChangeRequestEntity entity = repository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "change request not found"));
        GraphChangePayload payload = null;
        GraphValidationReport report = null;
        try {
            payload = objectMapper.readValue(entity.getPayloadJson(), GraphChangePayload.class);
        } catch (Exception ignored) {
            payload = null;
        }
        try {
            report = entity.getReportJson() == null ? null : objectMapper.readValue(entity.getReportJson(), GraphValidationReport.class);
        } catch (Exception ignored) {
            report = null;
        }
        return new GraphChangeRequestDetailDto(
                entity.getId(),
                entity.getKind(),
                entity.getPayloadType(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getSubmitNote(),
                entity.getReviewedBy(),
                entity.getReviewedAt(),
                entity.getReviewNote(),
                entity.getAppliedAt(),
                payload,
                report
        );
    }

    @Transactional
    public GraphChangeRequestDto startReview(Long id, GraphChangeRequestReviewRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        GraphChangeRequestEntity entity = repository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "change request not found"));
        if (entity.getStatus() != GraphChangeStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
        }
        entity.setStatus(GraphChangeStatus.REVIEWING);
        entity.setReviewedAt(Instant.now());
        entity.setReviewedBy(permissionService.currentUsername());
        entity.setReviewNote(trim(req == null ? null : req.getNote()));
        GraphChangeRequestEntity saved = repository.save(entity);
        logService.log("GRAPH_CHANGE_REVIEW_START", "id=" + saved.getId() + ", status=" + saved.getStatus());
        return toDto(saved);
    }

    @Transactional
    public GraphChangeRequestDto approve(Long id, GraphChangeRequestReviewRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        GraphChangeRequestEntity entity = repository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "change request not found"));
        if (entity.getStatus() != GraphChangeStatus.SUBMITTED && entity.getStatus() != GraphChangeStatus.REVIEWING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
        }
        GraphChangePayload payload = parsePayload(entity.getPayloadJson());
        ensurePayloadNotEmpty(payload);

        if (entity.getPayloadType() == GraphChangePayloadType.REPLACE) {
            GraphReplaceRequest replaceReq = toReplaceRequest(payload);
            graphImportService.replaceGraph(replaceReq);
        } else {
            GraphImportRequest importReq = toImportRequest(payload);
            graphImportService.importGraph(importReq);
        }

        entity.setStatus(GraphChangeStatus.APPLIED);
        entity.setReviewedAt(Instant.now());
        entity.setReviewedBy(permissionService.currentUsername());
        entity.setReviewNote(trim(req == null ? null : req.getNote()));
        entity.setAppliedAt(Instant.now());
        GraphChangeRequestEntity saved = repository.save(entity);
        logService.log("GRAPH_CHANGE_APPLY", "id=" + saved.getId() + ", status=" + saved.getStatus());
        return toDto(saved);
    }

    @Transactional
    public GraphChangeRequestDto reject(Long id, GraphChangeRequestReviewRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        GraphChangeRequestEntity entity = repository.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "change request not found"));
        if (entity.getStatus() != GraphChangeStatus.SUBMITTED && entity.getStatus() != GraphChangeStatus.REVIEWING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
        }
        entity.setStatus(GraphChangeStatus.REJECTED);
        entity.setReviewedAt(Instant.now());
        entity.setReviewedBy(permissionService.currentUsername());
        entity.setReviewNote(trim(req == null ? null : req.getNote()));
        GraphChangeRequestEntity saved = repository.save(entity);
        logService.log("GRAPH_CHANGE_REJECT", "id=" + saved.getId() + ", status=" + saved.getStatus());
        return toDto(saved);
    }

    private GraphChangeRequestDto toDto(GraphChangeRequestEntity entity) {
        return new GraphChangeRequestDto(
                entity.getId(),
                entity.getKind(),
                entity.getPayloadType(),
                entity.getStatus(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getSubmitNote(),
                entity.getReviewedBy(),
                entity.getReviewedAt(),
                entity.getReviewNote(),
                entity.getAppliedAt(),
                entity.getNodeCount(),
                entity.getEdgeCount(),
                entity.getQualityScore()
        );
    }

    private void ensurePayloadNotEmpty(GraphChangePayload payload) {
        if (payload == null || payload.getNodes() == null || payload.getEdges() == null
                || payload.getNodes().isEmpty() || payload.getEdges().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload empty");
        }
    }

    private GraphImportRequest toImportRequest(GraphChangePayload payload) {
        GraphImportRequest req = new GraphImportRequest();
        req.setNodes(payload.getNodes());
        req.setEdges(payload.getEdges());
        return req;
    }

    private GraphReplaceRequest toReplaceRequest(GraphChangePayload payload) {
        GraphReplaceRequest req = new GraphReplaceRequest();
        req.setNodes(payload.getNodes());
        req.setEdges(payload.getEdges());
        return req;
    }

    private GraphChangePayload parsePayload(String json) {
        if (json == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload missing");
        }
        try {
            return objectMapper.readValue(json, GraphChangePayload.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payload parse failed", e);
        }
    }

    private GraphChangeStatus parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        try {
            return GraphChangeStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

    private GraphChangeKind parseKind(String kind) {
        if (kind == null || kind.trim().isEmpty()) {
            return null;
        }
        try {
            return GraphChangeKind.valueOf(kind.trim().toUpperCase());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trim(String v) {
        if (v == null) {
            return null;
        }
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }
}
