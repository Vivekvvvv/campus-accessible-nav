package com.demo.accessiblenav.favorites.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class QuickRouteUpsertRequest {

    @NotBlank(message = "name is required")
    @Size(max = 128, message = "name too long")
    private String name;

    @NotNull(message = "startPlaceId is required")
    private Long startPlaceId;

    @NotNull(message = "endPlaceId is required")
    private Long endPlaceId;

    @Pattern(regexp = "(?i)WALK|WHEELCHAIR", message = "travelMode must be WALK/WHEELCHAIR")
    private String travelMode;

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
}
