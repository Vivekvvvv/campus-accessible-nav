package com.demo.accessiblenav.emergency.dto;

import com.demo.accessiblenav.emergency.ResponderType;

public class EmergencyDispatchTaskDto {

    private Long eventId;
    private String eventStatus;
    private String severity;
    private String volunteerUserId;
    private String volunteerName;
    private Double volunteerLat;
    private Double volunteerLng;
    private Double distanceMeters;
    private ResponderType responderType = ResponderType.VOLUNTEER;

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getEventStatus() { return eventStatus; }
    public void setEventStatus(String eventStatus) { this.eventStatus = eventStatus; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getVolunteerUserId() { return volunteerUserId; }
    public void setVolunteerUserId(String volunteerUserId) { this.volunteerUserId = volunteerUserId; }

    public String getVolunteerName() { return volunteerName; }
    public void setVolunteerName(String volunteerName) { this.volunteerName = volunteerName; }

    public Double getVolunteerLat() { return volunteerLat; }
    public void setVolunteerLat(Double volunteerLat) { this.volunteerLat = volunteerLat; }

    public Double getVolunteerLng() { return volunteerLng; }
    public void setVolunteerLng(Double volunteerLng) { this.volunteerLng = volunteerLng; }

    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }

    public ResponderType getResponderType() { return responderType; }
    public void setResponderType(ResponderType responderType) { this.responderType = responderType; }
}
