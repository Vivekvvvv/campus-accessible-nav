package com.demo.accessiblenav.route.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RouteRequest {

    @NotNull(message = "起点纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度值无效，范围应为 -90 到 90")
    @DecimalMax(value = "90.0", message = "纬度值无效，范围应为 -90 到 90")
    private Double startLat;

    @NotNull(message = "起点经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度值无效，范围应为 -180 到 180")
    @DecimalMax(value = "180.0", message = "经度值无效，范围应为 -180 到 180")
    private Double startLng;

    @NotNull(message = "终点纬度不能为空")
    @DecimalMin(value = "-90.0", message = "纬度值无效，范围应为 -90 到 90")
    @DecimalMax(value = "90.0", message = "纬度值无效，范围应为 -90 到 90")
    private Double endLat;

    @NotNull(message = "终点经度不能为空")
    @DecimalMin(value = "-180.0", message = "经度值无效，范围应为 -180 到 180")
    @DecimalMax(value = "180.0", message = "经度值无效，范围应为 -180 到 180")
    private Double endLng;

    @Min(value = -10, message = "楼层值无效，范围应为 -10 到 100")
    @Max(value = 100, message = "楼层值无效，范围应为 -10 到 100")
    private Integer startLevel = 1;

    @Min(value = -10, message = "楼层值无效，范围应为 -10 到 100")
    @Max(value = 100, message = "楼层值无效，范围应为 -10 到 100")
    private Integer endLevel = 1;

    @NotNull(message = "出行模式不能为空")
    private TravelMode mode;

    // Optional: route strategy (BALANCED/SHORTEST/SAFEST)
    private RouteStrategy strategy;

    // Optional: slope penalty weight (~0-1), default 0.2
    @DecimalMin(value = "0.0", message = "坡度权重最小为 0")
    @DecimalMax(value = "1.0", message = "坡度权重最大为 1")
    private Double slopeWeight;

    // Optional: fine-grained routing penalties for stairs/slope/construction.
    private RouteStrategyWeights strategyWeights;

    // Optional: only compute routes within campus bounds
    private Boolean campusOnly;

    public Double getStartLat() {
        return startLat;
    }

    public void setStartLat(Double startLat) {
        this.startLat = startLat;
    }

    public Double getStartLng() {
        return startLng;
    }

    public void setStartLng(Double startLng) {
        this.startLng = startLng;
    }

    public Double getEndLat() {
        return endLat;
    }

    public void setEndLat(Double endLat) {
        this.endLat = endLat;
    }

    public Double getEndLng() {
        return endLng;
    }

    public void setEndLng(Double endLng) {
        this.endLng = endLng;
    }

    public TravelMode getMode() {
        return mode;
    }

    public void setMode(TravelMode mode) {
        this.mode = mode;
    }

    public RouteStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(RouteStrategy strategy) {
        this.strategy = strategy;
    }

    public Double getSlopeWeight() {
        return slopeWeight;
    }

    public void setSlopeWeight(Double slopeWeight) {
        this.slopeWeight = slopeWeight;
    }

    public RouteStrategyWeights getStrategyWeights() {
        return strategyWeights;
    }

    public void setStrategyWeights(RouteStrategyWeights strategyWeights) {
        this.strategyWeights = strategyWeights;
    }

    public Boolean getCampusOnly() {
        return campusOnly;
    }

    public void setCampusOnly(Boolean campusOnly) {
        this.campusOnly = campusOnly;
    }

    public Integer getStartLevel() {
        return startLevel;
    }

    public void setStartLevel(Integer startLevel) {
        this.startLevel = startLevel;
    }

    public Integer getEndLevel() {
        return endLevel;
    }

    public void setEndLevel(Integer endLevel) {
        this.endLevel = endLevel;
    }
}
