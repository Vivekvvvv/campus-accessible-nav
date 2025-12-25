package com.demo.accessiblenav.navigation.dto;

/**
 * 导航指令消息
 * 推送给客户端的导航提示
 */
public class NavigationInstructionMessage {

    /**
     * 指令类型
     */
    private String action;

    /**
     * 距离下一个转弯点的距离（米）
     */
    private double distanceToNext;

    /**
     * 距离目的地的总距离（米）
     */
    private double distanceRemaining;

    /**
     * 预计剩余时间（秒）
     */
    private int estimatedTimeRemaining;

    /**
     * 语音播报文本
     */
    private String instruction;

    /**
     * 无障碍提示
     */
    private String accessibilityNote;

    /**
     * 地标参考
     */
    private String landmark;

    /**
     * 当前路段索引
     */
    private int currentSegmentIndex;

    /**
     * 总路段数
     */
    private int totalSegments;

    // Getters and Setters
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public double getDistanceToNext() { return distanceToNext; }
    public void setDistanceToNext(double distanceToNext) { this.distanceToNext = distanceToNext; }

    public double getDistanceRemaining() { return distanceRemaining; }
    public void setDistanceRemaining(double distanceRemaining) { this.distanceRemaining = distanceRemaining; }

    public int getEstimatedTimeRemaining() { return estimatedTimeRemaining; }
    public void setEstimatedTimeRemaining(int estimatedTimeRemaining) { this.estimatedTimeRemaining = estimatedTimeRemaining; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public String getAccessibilityNote() { return accessibilityNote; }
    public void setAccessibilityNote(String accessibilityNote) { this.accessibilityNote = accessibilityNote; }

    public String getLandmark() { return landmark; }
    public void setLandmark(String landmark) { this.landmark = landmark; }

    public int getCurrentSegmentIndex() { return currentSegmentIndex; }
    public void setCurrentSegmentIndex(int currentSegmentIndex) { this.currentSegmentIndex = currentSegmentIndex; }

    public int getTotalSegments() { return totalSegments; }
    public void setTotalSegments(int totalSegments) { this.totalSegments = totalSegments; }
}
