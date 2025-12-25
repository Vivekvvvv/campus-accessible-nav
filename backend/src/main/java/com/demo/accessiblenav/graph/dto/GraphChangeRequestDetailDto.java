package com.demo.accessiblenav.graph.dto;

import com.demo.accessiblenav.graph.change.GraphChangeKind;
import com.demo.accessiblenav.graph.change.GraphChangePayloadType;
import com.demo.accessiblenav.graph.change.GraphChangeStatus;

import java.time.Instant;

public class GraphChangeRequestDetailDto {

    private Long id;
    private GraphChangeKind kind;
    private GraphChangePayloadType payloadType;
    private GraphChangeStatus status;
    private String createdBy;
    private Instant createdAt;
    private String submitNote;
    private String reviewedBy;
    private Instant reviewedAt;
    private String reviewNote;
    private Instant appliedAt;
    private GraphChangePayload payload;
    private GraphValidationReport report;

    public GraphChangeRequestDetailDto(Long id,
                                       GraphChangeKind kind,
                                       GraphChangePayloadType payloadType,
                                       GraphChangeStatus status,
                                       String createdBy,
                                       Instant createdAt,
                                       String submitNote,
                                       String reviewedBy,
                                       Instant reviewedAt,
                                       String reviewNote,
                                       Instant appliedAt,
                                       GraphChangePayload payload,
                                       GraphValidationReport report) {
        this.id = id;
        this.kind = kind;
        this.payloadType = payloadType;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.submitNote = submitNote;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.reviewNote = reviewNote;
        this.appliedAt = appliedAt;
        this.payload = payload;
        this.report = report;
    }

    public Long getId() {
        return id;
    }

    public GraphChangeKind getKind() {
        return kind;
    }

    public GraphChangePayloadType getPayloadType() {
        return payloadType;
    }

    public GraphChangeStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getSubmitNote() {
        return submitNote;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public GraphChangePayload getPayload() {
        return payload;
    }

    public GraphValidationReport getReport() {
        return report;
    }
}
