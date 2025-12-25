package com.demo.accessiblenav.obstacle.dto;

import jakarta.validation.constraints.Size;

public class ObstacleReportReviewRequest {

    @Size(max = 512)
    private String reason;

    /**
     * 可选：审核通过后禁用多久（分钟）。不传则长期生效，直到手动解除。
     */
    private Integer durationMinutes;

    /**
     * 管理员审核备注
     */
    @Size(max = 1000)
    private String reviewNote;

    /**
     * 核查状态：VERIFIED(已核实), NEED_FIELD_CHECK(需实地核查), UNVERIFIED(未核查)
     */
    @Size(max = 32)
    private String verificationStatus;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
}
