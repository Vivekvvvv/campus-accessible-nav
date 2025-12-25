package com.demo.accessiblenav.graph.change;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "t_graph_change_request",
        indexes = {
                @Index(name = "idx_graph_change_created", columnList = "created_at"),
                @Index(name = "idx_graph_change_status", columnList = "status"),
                @Index(name = "idx_graph_change_kind", columnList = "kind")
        }
)
public class GraphChangeRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GraphChangeKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "payload_type", nullable = false, length = 16)
    private GraphChangePayloadType payloadType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GraphChangeStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "submit_note", length = 255)
    private String submitNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "review_note", length = 255)
    private String reviewNote;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "node_count", nullable = false)
    private int nodeCount;

    @Column(name = "edge_count", nullable = false)
    private int edgeCount;

    @Column(name = "quality_score", nullable = false)
    private double qualityScore;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    public Long getId() {
        return id;
    }

    public GraphChangeKind getKind() {
        return kind;
    }

    public void setKind(GraphChangeKind kind) {
        this.kind = kind;
    }

    public GraphChangePayloadType getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(GraphChangePayloadType payloadType) {
        this.payloadType = payloadType;
    }

    public GraphChangeStatus getStatus() {
        return status;
    }

    public void setStatus(GraphChangeStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getSubmitNote() {
        return submitNote;
    }

    public void setSubmitNote(String submitNote) {
        this.submitNote = submitNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(String reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public void setEdgeCount(int edgeCount) {
        this.edgeCount = edgeCount;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
    }
}
