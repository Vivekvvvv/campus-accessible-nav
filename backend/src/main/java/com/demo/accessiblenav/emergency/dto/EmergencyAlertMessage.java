package com.demo.accessiblenav.emergency.dto;

/**
 * 紧急事件WebSocket通知消息
 */
public class EmergencyAlertMessage {

    private Long eventId;
    private String userId;
    private String username;
    private String eventType;
    private String description;
    private Double lat;
    private Double lng;
    private String message;
    private String timestamp;

    public EmergencyAlertMessage() {}

    public EmergencyAlertMessage(Long eventId, String userId, String username,
                                  String eventType, String description,
                                  Double lat, Double lng, String message) {
        this.eventId = eventId;
        this.userId = userId;
        this.username = username;
        this.eventType = eventType;
        this.description = description;
        this.lat = lat;
        this.lng = lng;
        this.message = message;
        this.timestamp = java.time.Instant.now().toString();
    }

    // Getters and Setters
    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
