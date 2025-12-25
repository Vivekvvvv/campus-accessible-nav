package com.demo.accessiblenav.emergency;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 紧急事件实体
 */
@Entity
@Table(name = "t_emergency_event")
public class EmergencyEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(length = 128)
    private String username;

    @Column(name = "event_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private EmergencyType eventType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column
    private Double accuracy;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private EmergencyStatus status = EmergencyStatus.ACTIVE;

    @Column(name = "handled_by", length = 64)
    private String handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false, length = 16)
    private String severity = "NORMAL";

    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "broadcast_radius_m", nullable = false)
    private int broadcastRadiusM = 500;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId = "default";

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public EmergencyType getEventType() { return eventType; }
    public void setEventType(EmergencyType eventType) { this.eventType = eventType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public EmergencyStatus getStatus() { return status; }
    public void setStatus(EmergencyStatus status) { this.status = status; }

    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }

    public Instant getHandledAt() { return handledAt; }
    public void setHandledAt(Instant handledAt) { this.handledAt = handledAt; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public int getBroadcastRadiusM() { return broadcastRadiusM; }
    public void setBroadcastRadiusM(int broadcastRadiusM) { this.broadcastRadiusM = broadcastRadiusM; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
