package com.demo.accessiblenav.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(
        name = "t_accessibility_profile",
        indexes = {
                @Index(name = "idx_accessibility_profile_mode", columnList = "mobility_mode")
        }
)
public class AccessibilityProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @Column(name = "mobility_mode", nullable = false, length = 32)
    private String mobilityMode = "WALK";

    @Column(name = "avoid_stairs", nullable = false)
    private Boolean avoidStairs = false;

    @Column(name = "avoid_slope", nullable = false)
    private Boolean avoidSlope = false;

    @Column(name = "avoid_construction", nullable = false)
    private Boolean avoidConstruction = true;

    @Column(name = "max_slope_percent", nullable = false)
    private Double maxSlopePercent = 12.0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
