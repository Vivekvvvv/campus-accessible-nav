package com.demo.accessiblenav.favorites.dto;

import java.time.Instant;

public class QuickRouteDto {
    private Long id;
    private String name;
    private Long startPlaceId;
    private Long endPlaceId;
    private String travelMode;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getStartPlaceId() {
        return startPlaceId;
    }

    public void setStartPlaceId(Long startPlaceId) {
        this.startPlaceId = startPlaceId;
    }

    public Long getEndPlaceId() {
        return endPlaceId;
    }

    public void setEndPlaceId(Long endPlaceId) {
        this.endPlaceId = endPlaceId;
    }

    public String getTravelMode() {
        return travelMode;
    }

    public void setTravelMode(String travelMode) {
        this.travelMode = travelMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
