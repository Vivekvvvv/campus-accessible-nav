package com.demo.accessiblenav.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@Schema(name = "RoutePassabilityPolicyUpdateRequest", description = "Update request for route passability policy")
public class RoutePassabilityPolicyUpdateRequest {

    @Schema(description = "Whether passability penalty should be enabled", example = "true")
    private Boolean passabilityPenaltyEnabled;

    @Schema(description = "Minimum clamp for passability probability [0.01, 0.5]", example = "0.05")
    @DecimalMin(value = "0.01", message = "passabilityMinClamp must be >= 0.01")
    @DecimalMax(value = "0.5", message = "passabilityMinClamp must be <= 0.5")
    private Double passabilityMinClamp;

    @Schema(description = "Dynamic weight factor [0, 2]", example = "1.0")
    @DecimalMin(value = "0.0", message = "passabilityWeightFactor must be >= 0")
    @DecimalMax(value = "2.0", message = "passabilityWeightFactor must be <= 2")
    private Double passabilityWeightFactor;

    public Boolean getPassabilityPenaltyEnabled() {
        return passabilityPenaltyEnabled;
    }

    public void setPassabilityPenaltyEnabled(Boolean passabilityPenaltyEnabled) {
        this.passabilityPenaltyEnabled = passabilityPenaltyEnabled;
    }

    public Double getPassabilityMinClamp() {
        return passabilityMinClamp;
    }

    public void setPassabilityMinClamp(Double passabilityMinClamp) {
        this.passabilityMinClamp = passabilityMinClamp;
    }

    public Double getPassabilityWeightFactor() {
        return passabilityWeightFactor;
    }

    public void setPassabilityWeightFactor(Double passabilityWeightFactor) {
        this.passabilityWeightFactor = passabilityWeightFactor;
    }
}
