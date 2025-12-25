package com.demo.accessiblenav.obstacle;

import com.demo.accessiblenav.graph.EdgeEntity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "t_obstacle_report",
        indexes = {
                @Index(name = "idx_obstacle_edge", columnList = "edge_id"),
                @Index(name = "idx_obstacle_status", columnList = "status"),
                @Index(name = "idx_obstacle_created", columnList = "created_at")
        }
)
public class ObstacleReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "edge_id", nullable = false)
    private EdgeEntity edge;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 512)
    private String reason;

    // 照片证据（存储 URL 列表，逗号分隔）
    @Column(name = "photo_urls", length = 2000)
    private String photoUrls;

    // 提交者位置（GPS）
    @Column(name = "submitter_lat")
    private Double submitterLat;

    @Column(name = "submitter_lng")
    private Double submitterLng;

    // 提交者信息
    @Column(name = "submitter_id", length = 64)
    private String submitterId;

    @Column(name = "submitter_name", length = 128)
    private String submitterName;

    // 管理员审核信息
    @Column(name = "reviewer_id", length = 64)
    private String reviewerId;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    // 核查状态（未核查/待实地核查/已核查）
    @Column(name = "verification_status", length = 32)
    private String verificationStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "dedupe_key", length = 64)
    private String dedupeKey;

    @Column(name = "confirm_count", nullable = false)
    private int confirmCount = 1;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "escalated", nullable = false)
    private boolean escalated = false;

    @Column(name = "escalated_at")
    private Instant escalatedAt;

    @Column(name = "assigned_reviewer", length = 64)
    private String assignedReviewer;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId = "default";

    public Long getId() {
        return id;
    }

    public EdgeEntity getEdge() {
        return edge;
    }

    public void setEdge(EdgeEntity edge) {
        this.edge = edge;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(String photoUrls) {
        this.photoUrls = photoUrls;
    }

    public Double getSubmitterLat() {
        return submitterLat;
    }

    public void setSubmitterLat(Double submitterLat) {
        this.submitterLat = submitterLat;
    }

    public Double getSubmitterLng() {
        return submitterLng;
    }

    public void setSubmitterLng(Double submitterLng) {
        this.submitterLng = submitterLng;
    }

    public String getSubmitterId() {
        return submitterId;
    }

    public void setSubmitterId(String submitterId) {
        this.submitterId = submitterId;
    }

    public String getSubmitterName() {
        return submitterName;
    }

    public void setSubmitterName(String submitterName) {
        this.submitterName = submitterName;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(String reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public int getConfirmCount() {
        return confirmCount;
    }

    public void setConfirmCount(int confirmCount) {
        this.confirmCount = confirmCount;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public boolean isEscalated() {
        return escalated;
    }

    public void setEscalated(boolean escalated) {
        this.escalated = escalated;
    }

    public Instant getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(Instant escalatedAt) {
        this.escalatedAt = escalatedAt;
    }

    public String getAssignedReviewer() {
        return assignedReviewer;
    }

    public void setAssignedReviewer(String assignedReviewer) {
        this.assignedReviewer = assignedReviewer;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
