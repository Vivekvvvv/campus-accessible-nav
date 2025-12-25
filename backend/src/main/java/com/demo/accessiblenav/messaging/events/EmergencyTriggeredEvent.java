package com.demo.accessiblenav.messaging.events;

import com.demo.accessiblenav.messaging.DomainEvent;

/**
 * 紧急求助事件
 */
public class EmergencyTriggeredEvent extends DomainEvent {

    private String userId;
    private String username;
    private double lat;
    private double lng;
    private String emergencyType;
    private String description;

    public EmergencyTriggeredEvent(String eventId, String userId) {
        super(eventId, "Emergency");
        this.userId = userId;
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public String getEmergencyType() { return emergencyType; }
    public void setEmergencyType(String emergencyType) { this.emergencyType = emergencyType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
