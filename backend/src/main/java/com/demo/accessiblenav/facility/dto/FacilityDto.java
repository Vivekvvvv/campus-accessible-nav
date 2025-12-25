package com.demo.accessiblenav.facility.dto;

import com.demo.accessiblenav.facility.FacilityType;

import java.time.Instant;
import java.util.List;

/**
 * 设施响应 DTO
 */
public class FacilityDto {

    private Long id;
    private FacilityType facilityType;
    private String facilityTypeName;
    private String name;
    private String description;
    private String buildingName;
    private Integer floorLevel;
    private Double lat;
    private Double lng;
    private String openingHours;
    private Boolean isOperational;
    private Instant lastVerifiedAt;
    private String photoUrl;
    private String contactInfo;
    private String notes;
    private List<FeatureDto> features;
    private Double distanceMeters; // 用于附近搜索时返回距离

    public static class FeatureDto {
        private String featureType;
        private String featureValue;
        private String notes;

        public String getFeatureType() { return featureType; }
        public void setFeatureType(String featureType) { this.featureType = featureType; }
        public String getFeatureValue() { return featureValue; }
        public void setFeatureValue(String featureValue) { this.featureValue = featureValue; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FacilityType getFacilityType() { return facilityType; }
    public void setFacilityType(FacilityType facilityType) { this.facilityType = facilityType; }

    public String getFacilityTypeName() { return facilityTypeName; }
    public void setFacilityTypeName(String facilityTypeName) { this.facilityTypeName = facilityTypeName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBuildingName() { return buildingName; }
    public void setBuildingName(String buildingName) { this.buildingName = buildingName; }

    public Integer getFloorLevel() { return floorLevel; }
    public void setFloorLevel(Integer floorLevel) { this.floorLevel = floorLevel; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    public Boolean getIsOperational() { return isOperational; }
    public void setIsOperational(Boolean isOperational) { this.isOperational = isOperational; }

    public Instant getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(Instant lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getContactInfo() { return contactInfo; }
    public void setContactInfo(String contactInfo) { this.contactInfo = contactInfo; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<FeatureDto> getFeatures() { return features; }
    public void setFeatures(List<FeatureDto> features) { this.features = features; }

    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
}
