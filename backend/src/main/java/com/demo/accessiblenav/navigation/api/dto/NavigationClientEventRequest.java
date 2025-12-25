package com.demo.accessiblenav.navigation.api.dto;

import java.time.Instant;

public class NavigationClientEventRequest {
    /**
     * Event type, e.g. TURN_ANNOUNCED / PROGRESS_ANNOUNCED / OFF_ROUTE_WARNED / HAZARD_WARNED.
     */
    private String type;

    /**
     * Optional small payload for debugging (kept as string to avoid schema churn).
     */
    private String payload;

    /**
     * Client-side timestamp (optional).
     */
    private Instant observedAt;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }
}

