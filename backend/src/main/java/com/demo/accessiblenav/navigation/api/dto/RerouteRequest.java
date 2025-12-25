package com.demo.accessiblenav.navigation.api.dto;

public class RerouteRequest {
    private double lat;
    private double lng;
    /**
     * Optional reason: e.g. DEVIATION
     */
    private String reason;

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

