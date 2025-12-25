package com.demo.accessiblenav.route;

/**
 * 导航指令
 */
public class NavigationInstruction {

    private int stepNumber;           // 步骤编号
    private String action;            // 动作：直行、左转、右转、到达等
    private double distanceMeters;    // 距离（米）
    private String distanceText;      // 距离文本
    private String description;       // 描述文本（用于语音播报）
    private String landmark;          // 附近地标（可选）
    private String accessibilityNote; // 无障碍提示（可选）
    private double bearing;           // 方向角度
    private Double lat;               // 该步骤终点纬度
    private Double lng;               // 该步骤终点经度
    private Integer level;            // 楼层

    // Getters and Setters
    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(double distanceMeters) { this.distanceMeters = distanceMeters; }

    public String getDistanceText() { return distanceText; }
    public void setDistanceText(String distanceText) { this.distanceText = distanceText; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLandmark() { return landmark; }
    public void setLandmark(String landmark) { this.landmark = landmark; }

    public String getAccessibilityNote() { return accessibilityNote; }
    public void setAccessibilityNote(String accessibilityNote) { this.accessibilityNote = accessibilityNote; }

    public double getBearing() { return bearing; }
    public void setBearing(double bearing) { this.bearing = bearing; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
}
