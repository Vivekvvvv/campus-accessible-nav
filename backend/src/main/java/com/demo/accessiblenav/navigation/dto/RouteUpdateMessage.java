package com.demo.accessiblenav.navigation.dto;

import java.util.List;

/**
 * 路线更新消息
 * 当用户偏离路线时推送新路线
 */
public class RouteUpdateMessage {

    /**
     * 新路线坐标
     */
    private List<double[]> coordinates;

    /**
     * 新路线总距离（米）
     */
    private double distanceMeters;

    /**
     * 预计时间（秒）
     */
    private int estimatedTime;

    /**
     * 更新原因
     */
    private String reason;

    /**
     * 提示消息
     */
    private String message;

    public RouteUpdateMessage() {}

    public RouteUpdateMessage(List<double[]> coordinates, double distanceMeters, String message) {
        this.coordinates = coordinates;
        this.distanceMeters = distanceMeters;
        this.message = message;
        this.estimatedTime = (int) (distanceMeters / 1.2); // 假设步行速度1.2m/s
    }

    // Getters and Setters
    public List<double[]> getCoordinates() { return coordinates; }
    public void setCoordinates(List<double[]> coordinates) { this.coordinates = coordinates; }

    public double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(double distanceMeters) { this.distanceMeters = distanceMeters; }

    public int getEstimatedTime() { return estimatedTime; }
    public void setEstimatedTime(int estimatedTime) { this.estimatedTime = estimatedTime; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
