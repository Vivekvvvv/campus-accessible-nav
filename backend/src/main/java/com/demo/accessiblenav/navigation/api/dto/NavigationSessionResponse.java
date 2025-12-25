package com.demo.accessiblenav.navigation.api.dto;

import com.demo.accessiblenav.navigation.persistence.NavigationSessionStatus;
import com.demo.accessiblenav.route.dto.RouteResponse;

import java.time.Instant;
import java.util.List;

public class NavigationSessionResponse {
    private String sessionId;
    private String userId;
    private NavigationSessionStatus status;
    private String mode;

    private double startLat;
    private double startLng;
    private double destinationLat;
    private double destinationLng;
    private String destinationName;

    private Double lastLat;
    private Double lastLng;
    private Instant lastLocationAt;

    private int deviationCount;
    private int rerouteCount;

    private Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;

    private String resumeToken;

    private RouteResponse route;

    private List<WaypointDto> waypoints;
    private int currentLeg;
    private int totalLegs = 1;
    private Integer currentLevel;
    private Integer nextLevel;
    private String levelTransitionVia;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public NavigationSessionStatus getStatus() {
        return status;
    }

    public void setStatus(NavigationSessionStatus status) {
        this.status = status;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public double getStartLat() {
        return startLat;
    }

    public void setStartLat(double startLat) {
        this.startLat = startLat;
    }

    public double getStartLng() {
        return startLng;
    }

    public void setStartLng(double startLng) {
        this.startLng = startLng;
    }

    public double getDestinationLat() {
        return destinationLat;
    }

    public void setDestinationLat(double destinationLat) {
        this.destinationLat = destinationLat;
    }

    public double getDestinationLng() {
        return destinationLng;
    }

    public void setDestinationLng(double destinationLng) {
        this.destinationLng = destinationLng;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public Double getLastLat() {
        return lastLat;
    }

    public void setLastLat(Double lastLat) {
        this.lastLat = lastLat;
    }

    public Double getLastLng() {
        return lastLng;
    }

    public void setLastLng(Double lastLng) {
        this.lastLng = lastLng;
    }

    public Instant getLastLocationAt() {
        return lastLocationAt;
    }

    public void setLastLocationAt(Instant lastLocationAt) {
        this.lastLocationAt = lastLocationAt;
    }

    public int getDeviationCount() {
        return deviationCount;
    }

    public void setDeviationCount(int deviationCount) {
        this.deviationCount = deviationCount;
    }

    public int getRerouteCount() {
        return rerouteCount;
    }

    public void setRerouteCount(int rerouteCount) {
        this.rerouteCount = rerouteCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public RouteResponse getRoute() {
        return route;
    }

    public void setRoute(RouteResponse route) {
        this.route = route;
    }

    public String getResumeToken() {
        return resumeToken;
    }

    public void setResumeToken(String resumeToken) {
        this.resumeToken = resumeToken;
    }

    public List<WaypointDto> getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(List<WaypointDto> waypoints) {
        this.waypoints = waypoints;
    }

    public int getCurrentLeg() {
        return currentLeg;
    }

    public void setCurrentLeg(int currentLeg) {
        this.currentLeg = currentLeg;
    }

    public int getTotalLegs() {
        return totalLegs;
    }

    public void setTotalLegs(int totalLegs) {
        this.totalLegs = totalLegs;
    }

    public Integer getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(Integer currentLevel) {
        this.currentLevel = currentLevel;
    }

    public Integer getNextLevel() {
        return nextLevel;
    }

    public void setNextLevel(Integer nextLevel) {
        this.nextLevel = nextLevel;
    }

    public String getLevelTransitionVia() {
        return levelTransitionVia;
    }

    public void setLevelTransitionVia(String levelTransitionVia) {
        this.levelTransitionVia = levelTransitionVia;
    }
}
