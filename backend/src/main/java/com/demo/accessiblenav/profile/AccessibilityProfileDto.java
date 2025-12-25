package com.demo.accessiblenav.profile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

@Schema(name = "AccessibilityProfile")
public class AccessibilityProfileDto {

    @Schema(
            description = "Mobility mode for personalized routing",
            allowableValues = {"WALK", "WHEELCHAIR", "VISUAL_IMPAIRMENT", "STROLLER"},
            example = "WHEELCHAIR"
    )
    @Pattern(
            regexp = "(?i)WALK|WHEELCHAIR|VISUAL_IMPAIRMENT|STROLLER",
            message = "mobilityMode must be WALK/WHEELCHAIR/VISUAL_IMPAIRMENT/STROLLER"
    )
    private String mobilityMode;

    @Schema(description = "Avoid stair-heavy segments", example = "true")
    private Boolean avoidStairs;

    @Schema(description = "Prefer lower slope routes", example = "true")
    private Boolean avoidSlope;

    @Schema(description = "Avoid construction-affected segments when possible", example = "true")
    private Boolean avoidConstruction;

    @Schema(description = "Maximum acceptable slope percentage", example = "8.5")
    @DecimalMin(value = "0.0", message = "maxSlopePercent must be >= 0")
    @DecimalMax(value = "45.0", message = "maxSlopePercent must be <= 45")
    private Double maxSlopePercent;

    public String getMobilityMode() {
        return mobilityMode;
    }

    public void setMobilityMode(String mobilityMode) {
        this.mobilityMode = mobilityMode;
    }

    public Boolean getAvoidStairs() {
        return avoidStairs;
    }

    public void setAvoidStairs(Boolean avoidStairs) {
        this.avoidStairs = avoidStairs;
    }

    public Boolean getAvoidSlope() {
        return avoidSlope;
    }

    public void setAvoidSlope(Boolean avoidSlope) {
        this.avoidSlope = avoidSlope;
    }

    public Boolean getAvoidConstruction() {
        return avoidConstruction;
    }

    public void setAvoidConstruction(Boolean avoidConstruction) {
        this.avoidConstruction = avoidConstruction;
    }

    public Double getMaxSlopePercent() {
        return maxSlopePercent;
    }

    public void setMaxSlopePercent(Double maxSlopePercent) {
        this.maxSlopePercent = maxSlopePercent;
    }
}
