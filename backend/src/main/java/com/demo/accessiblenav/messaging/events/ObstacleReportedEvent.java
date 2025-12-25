package com.demo.accessiblenav.messaging.events;

import com.demo.accessiblenav.messaging.DomainEvent;

/**
 * 障碍上报事件
 */
public class ObstacleReportedEvent extends DomainEvent {

    private String reporterId;
    private double lat;
    private double lng;
    private String obstacleType;
    private String description;
    private boolean requiresReview;

    public ObstacleReportedEvent(String obstacleId, String reporterId) {
        super(obstacleId, "Obstacle");
        this.reporterId = reporterId;
    }

    // Getters and Setters
    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public String getObstacleType() { return obstacleType; }
    public void setObstacleType(String obstacleType) { this.obstacleType = obstacleType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isRequiresReview() { return requiresReview; }
    public void setRequiresReview(boolean requiresReview) { this.requiresReview = requiresReview; }
}
