package com.demo.accessiblenav.favorites.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class FavoritePlaceUpsertRequest {

    private Long groupId;

    @NotBlank(message = "name is required")
    @Size(max = 128, message = "name too long")
    private String name;

    @DecimalMin(value = "-90.0", message = "lat invalid")
    @DecimalMax(value = "90.0", message = "lat invalid")
    private Double lat;

    @DecimalMin(value = "-180.0", message = "lng invalid")
    @DecimalMax(value = "180.0", message = "lng invalid")
    private Double lng;

    private List<String> tags;

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
