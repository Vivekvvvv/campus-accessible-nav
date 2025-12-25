package com.demo.accessiblenav.obstacle.dto;

import java.time.Instant;
import java.util.List;

public class ObstacleReportDto {

    private Long id;
    private Long edgeId;
    private String type;
    private String status;
    private String reason;
    private Instant createdAt;

    // 新增的验证相关字段
    private List<String> photoUrls;
    private Double submitterLat;
    private Double submitterLng;
    private String submitterName;
    private String reviewNote;
    private Instant reviewedAt;
    private String verificationStatus;
    private int confirmCount;
    private Double confidenceScore;
    private boolean escalated;
    private Instant escalatedAt;

    public ObstacleReportDto() {
    }

    public ObstacleReportDto(Long id, Long edgeId, String type, String status, String reason, Instant createdAt) {
        this.id = id;
        this.edgeId = edgeId;
        this.type = type;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(Long edgeId) {
        this.edgeId = edgeId;
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

    // 新增字段的 getters 和 setters
    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(List<String> photoUrls) {
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

    public String getSubmitterName() {
        return submitterName;
    }

    public void setSubmitterName(String submitterName) {
        this.submitterName = submitterName;
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
}
