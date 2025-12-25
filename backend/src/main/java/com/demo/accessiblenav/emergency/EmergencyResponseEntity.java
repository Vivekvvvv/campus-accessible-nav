package com.demo.accessiblenav.emergency;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 紧急事件响应记录实体
 */
@Entity
@Table(name = "t_emergency_response")
public class EmergencyResponseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private EmergencyEventEntity event;

    @Column(name = "responder_type", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private ResponderType responderType;

    @Column(name = "responder_id", length = 64)
    private String responderId;

    @Column(name = "responder_name", length = 64)
    private String responderName;

    @Column(name = "response_time", nullable = false)
    private Instant responseTime;

    @Column(name = "arrival_time")
    private Instant arrivalTime;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId = "default";

    @PrePersist
    protected void onCreate() {
        if (responseTime == null) {
            responseTime = Instant.now();
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EmergencyEventEntity getEvent() { return event; }
    public void setEvent(EmergencyEventEntity event) { this.event = event; }

    public ResponderType getResponderType() { return responderType; }
    public void setResponderType(ResponderType responderType) { this.responderType = responderType; }

    public String getResponderId() { return responderId; }
    public void setResponderId(String responderId) { this.responderId = responderId; }

    public String getResponderName() { return responderName; }
    public void setResponderName(String responderName) { this.responderName = responderName; }

    public Instant getResponseTime() { return responseTime; }
    public void setResponseTime(Instant responseTime) { this.responseTime = responseTime; }

    public Instant getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(Instant arrivalTime) { this.arrivalTime = arrivalTime; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
