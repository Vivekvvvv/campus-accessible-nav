package com.demo.accessiblenav.navigation.api.dto;

public class WaypointDto {
    private double lat;
    private double lng;
    private String name;
    private boolean reached;

    public WaypointDto() {
    }

    public WaypointDto(double lat, double lng, String name, boolean reached) {
        this.lat = lat;
        this.lng = lng;
        this.name = name;
        this.reached = reached;
    }

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isReached() {
        return reached;
    }

    public void setReached(boolean reached) {
        this.reached = reached;
    }
}
