package com.demo.accessiblenav.emergency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class EmergencyBroadcastRequest {

    private Long eventId;

    @NotBlank(message = "broadcast scope is required")
    @Pattern(regexp = "SECURITY|VOLUNTEER|ALL", message = "scope must be SECURITY|VOLUNTEER|ALL")
    private String targetScope;

    @NotBlank(message = "severity is required")
    @Pattern(regexp = "NORMAL|HIGH|CRITICAL", message = "severity must be NORMAL|HIGH|CRITICAL")
    private String severity;

    @NotBlank(message = "message is required")
    @Size(max = 500, message = "message length must be <= 500")
    private String message;

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getTargetScope() { return targetScope; }
    public void setTargetScope(String targetScope) { this.targetScope = targetScope; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
