package com.demo.accessiblenav.obstacle;

import com.demo.accessiblenav.graph.EdgeEntity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "t_obstacle_effect",
        indexes = {
                @Index(name = "idx_effect_edge", columnList = "edge_id"),
                @Index(name = "idx_effect_active", columnList = "active"),
                @Index(name = "idx_effect_report", columnList = "report_id"),
                @Index(name = "idx_effect_end_at", columnList = "end_at")
        }
)
public class ObstacleEffectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "edge_id", nullable = false)
    private EdgeEntity edge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id")
    private ObstacleReportEntity report;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "disabled", nullable = false)
    private boolean disabled;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(length = 512)
    private String reason;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 64)
    private String revokedBy;

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

    public ObstacleReportEntity getReport() {
        return report;
    }

    public void setReport(ObstacleReportEntity report) {
        this.report = report;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(String revokedBy) {
        this.revokedBy = revokedBy;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
