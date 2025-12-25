package com.demo.accessiblenav.navigation;

import java.time.Instant;
import java.util.List;

/**
 * 导航会话
 * 存储用户当前的导航状态
 */
public class NavigationSession {

    private String sessionId;
    private String oderId;
    private double destinationLat;
    private double destinationLng;
    private String destinationName;
    private String mode; // WALK or WHEELCHAIR
    private List<double[]> routeCoordinates;
    private int currentSegmentIndex;
    private Instant startedAt;
    private Instant lastUpdateAt;
    private boolean active;

    public NavigationSession() {
        this.active = true;
        this.currentSegmentIndex = 0;
        this.startedAt = Instant.now();
        this.lastUpdateAt = Instant.now();
    }

    // Getters and Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return oderId; }
    public void setUserId(String oderId) { this.oderId = oderId; }

    public double getDestinationLat() { return destinationLat; }
    public void setDestinationLat(double destinationLat) { this.destinationLat = destinationLat; }

    public double getDestinationLng() { return destinationLng; }
    public void setDestinationLng(double destinationLng) { this.destinationLng = destinationLng; }

    public String getDestinationName() { return destinationName; }
    public void setDestinationName(String destinationName) { this.destinationName = destinationName; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public List<double[]> getRouteCoordinates() { return routeCoordinates; }
    public void setRouteCoordinates(List<double[]> routeCoordinates) { this.routeCoordinates = routeCoordinates; }

    public int getCurrentSegmentIndex() { return currentSegmentIndex; }
    public void setCurrentSegmentIndex(int currentSegmentIndex) { this.currentSegmentIndex = currentSegmentIndex; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getLastUpdateAt() { return lastUpdateAt; }
    public void setLastUpdateAt(Instant lastUpdateAt) { this.lastUpdateAt = lastUpdateAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
