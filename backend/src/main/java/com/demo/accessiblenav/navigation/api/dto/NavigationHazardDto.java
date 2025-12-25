package com.demo.accessiblenav.navigation.api.dto;

import java.time.Instant;

/**
 * A lightweight "hazard on/near the current route" representation for navigation sessions.
 *
 * This is derived from active obstacle effects and the session's stored route geometry.
 */
public class NavigationHazardDto {

    private Long effectId;
    private Long edgeId;
    private String reason;
    private Instant endAt;

    private double fromLat;
    private double fromLng;
    private double toLat;
    private double toLng;

    // Distance from hazard (edge midpoint) to the session route polyline (meters).
    private double distanceToRouteM;
    // Location along the route polyline where the hazard is closest (meters from route start).
    private double routeAtM;

    public NavigationHazardDto() {
    }

    public NavigationHazardDto(Long effectId,
                               Long edgeId,
                               String reason,
                               Instant endAt,
                               double fromLat,
                               double fromLng,
                               double toLat,
                               double toLng,
                               double distanceToRouteM,
                               double routeAtM) {
        this.effectId = effectId;
        this.edgeId = edgeId;
        this.reason = reason;
        this.endAt = endAt;
        this.fromLat = fromLat;
        this.fromLng = fromLng;
        this.toLat = toLat;
        this.toLng = toLng;
        this.distanceToRouteM = distanceToRouteM;
        this.routeAtM = routeAtM;
    }

    public Long getEffectId() {
        return effectId;
    }

    public void setEffectId(Long effectId) {
        this.effectId = effectId;
    }

    public Long getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(Long edgeId) {
        this.edgeId = edgeId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public double getFromLat() {
        return fromLat;
    }

    public void setFromLat(double fromLat) {
        this.fromLat = fromLat;
    }

    public double getFromLng() {
        return fromLng;
    }

    public void setFromLng(double fromLng) {
        this.fromLng = fromLng;
    }

    public double getToLat() {
        return toLat;
    }

    public void setToLat(double toLat) {
        this.toLat = toLat;
    }

    public double getToLng() {
        return toLng;
    }

    public void setToLng(double toLng) {
        this.toLng = toLng;
    }

    public double getDistanceToRouteM() {
        return distanceToRouteM;
    }

    public void setDistanceToRouteM(double distanceToRouteM) {
        this.distanceToRouteM = distanceToRouteM;
    }

    public double getRouteAtM() {
        return routeAtM;
    }

    public void setRouteAtM(double routeAtM) {
        this.routeAtM = routeAtM;
    }
}

