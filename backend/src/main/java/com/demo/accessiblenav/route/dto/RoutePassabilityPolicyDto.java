package com.demo.accessiblenav.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "RoutePassabilityPolicy", description = "Dynamic passability penalty policy for route search")
public class RoutePassabilityPolicyDto {

    @Schema(description = "Tenant ID owning this policy", example = "default")
    private String tenantId;

    @Schema(description = "Whether passability penalty is enabled", example = "true")
    private boolean passabilityPenaltyEnabled;

    @Schema(description = "Minimum clamp for passability probability", example = "0.05")
    private double passabilityMinClamp;

    @Schema(description = "Passability dynamic weight factor [0,2]", example = "1.0")
    private double passabilityWeightFactor;

    @Schema(description = "Who updated this policy last", example = "admin")
    private String updatedBy;

    @Schema(description = "When this policy was last updated")
    private Instant updatedAt;

    public RoutePassabilityPolicyDto() {
    }

    public RoutePassabilityPolicyDto(String tenantId,
                                     boolean passabilityPenaltyEnabled,
                                     double passabilityMinClamp,
                                     double passabilityWeightFactor,
                                     String updatedBy,
                                     Instant updatedAt) {
        this.tenantId = tenantId;
        this.passabilityPenaltyEnabled = passabilityPenaltyEnabled;
        this.passabilityMinClamp = passabilityMinClamp;
        this.passabilityWeightFactor = passabilityWeightFactor;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public boolean isPassabilityPenaltyEnabled() {
        return passabilityPenaltyEnabled;
    }

    public void setPassabilityPenaltyEnabled(boolean passabilityPenaltyEnabled) {
        this.passabilityPenaltyEnabled = passabilityPenaltyEnabled;
    }

    public double getPassabilityMinClamp() {
        return passabilityMinClamp;
    }

    public void setPassabilityMinClamp(double passabilityMinClamp) {
        this.passabilityMinClamp = passabilityMinClamp;
    }

    public double getPassabilityWeightFactor() {
        return passabilityWeightFactor;
    }

    public void setPassabilityWeightFactor(double passabilityWeightFactor) {
        this.passabilityWeightFactor = passabilityWeightFactor;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
