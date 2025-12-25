package com.demo.accessiblenav.emergency;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "t_emergency_broadcast")
public class EmergencyBroadcastEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private EmergencyEventEntity event;

    @Column(name = "publisher_id", nullable = false, length = 64)
    private String publisherId;

    @Column(name = "target_scope", nullable = false, length = 16)
    private String targetScope;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId = "default";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }

    public EmergencyEventEntity getEvent() { return event; }
    public void setEvent(EmergencyEventEntity event) { this.event = event; }

    public String getPublisherId() { return publisherId; }
    public void setPublisherId(String publisherId) { this.publisherId = publisherId; }

    public String getTargetScope() { return targetScope; }
    public void setTargetScope(String targetScope) { this.targetScope = targetScope; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Instant getCreatedAt() { return createdAt; }
}
