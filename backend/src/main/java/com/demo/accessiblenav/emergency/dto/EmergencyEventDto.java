package com.demo.accessiblenav.emergency.dto;

import com.demo.accessiblenav.emergency.EmergencyStatus;
import com.demo.accessiblenav.emergency.EmergencyType;

import java.time.Instant;

/**
 * 紧急事件响应DTO
 */
public class EmergencyEventDto {

    private Long id;
    private String userId;
    private String username;
    private EmergencyType eventType;
    private String eventTypeDisplay;
    private String description;
    private Double lat;
    private Double lng;
    private Double accuracy;
    private EmergencyStatus status;
    private String statusDisplay;
    private String handledBy;
    private Instant handledAt;
    private String resolutionNote;
    private Instant createdAt;
    private Instant updatedAt;
    private String severity;
    private Long zoneId;
    private Integer broadcastRadiusM;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public EmergencyType getEventType() { return eventType; }
    public void setEventType(EmergencyType eventType) { this.eventType = eventType; }

    public String getEventTypeDisplay() { return eventTypeDisplay; }
    public void setEventTypeDisplay(String eventTypeDisplay) { this.eventTypeDisplay = eventTypeDisplay; }

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

    public String getStatusDisplay() { return statusDisplay; }
    public void setStatusDisplay(String statusDisplay) { this.statusDisplay = statusDisplay; }

    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }

    public Instant getHandledAt() { return handledAt; }
    public void setHandledAt(Instant handledAt) { this.handledAt = handledAt; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public Long getZoneId() { return zoneId; }
    public void setZoneId(Long zoneId) { this.zoneId = zoneId; }

    public Integer getBroadcastRadiusM() { return broadcastRadiusM; }
    public void setBroadcastRadiusM(Integer broadcastRadiusM) { this.broadcastRadiusM = broadcastRadiusM; }
}
