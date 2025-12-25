package com.demo.accessiblenav.emergency.dto;

import com.demo.accessiblenav.emergency.EmergencyType;

import jakarta.validation.constraints.NotNull;

/**
 * 触发紧急求助请求
 */
public class TriggerEmergencyRequest {

    @NotNull(message = "紧急事件类型不能为空")
    private EmergencyType eventType;

    private String description;

    @NotNull(message = "纬度不能为空")
    private Double lat;

    @NotNull(message = "经度不能为空")
    private Double lng;

    private Double accuracy;

    private String severity;

    // Getters and Setters
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

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}
