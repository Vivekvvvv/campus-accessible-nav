package com.demo.accessiblenav.navigation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 位置更新请求
 */
public class LocationUpdateRequest {

    @NotNull(message = "纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度范围无效")
    @DecimalMax(value = "90.0", message = "纬度范围无效")
    private Double lat;

    @NotNull(message = "经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度范围无效")
    @DecimalMax(value = "180.0", message = "经度范围无效")
    private Double lng;

    /**
     * 定位精度（米）
     */
    private Double accuracy;

    /**
     * 移动方向（度，0-360）
     */
    private Double heading;

    /**
     * 移动速度（米/秒）
     */
    private Double speed;

    /**
     * 时间戳
     */
    private Long timestamp;

    // Getters and Setters
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public Double getHeading() { return heading; }
    public void setHeading(Double heading) { this.heading = heading; }

    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
