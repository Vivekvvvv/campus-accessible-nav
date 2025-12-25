package com.demo.accessiblenav.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "t_route_passability_policy",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_route_passability_policy_tenant", columnNames = "tenant_id")
        })
public class RoutePassabilityPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId = "default";

    @Column(name = "passability_penalty_enabled", nullable = false)
    private boolean passabilityPenaltyEnabled = true;

    @Column(name = "passability_min_clamp", nullable = false)
    private double passabilityMinClamp = 0.01;

    @Column(name = "passability_weight_factor", nullable = false)
    private double passabilityWeightFactor = 1.0;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
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
}
