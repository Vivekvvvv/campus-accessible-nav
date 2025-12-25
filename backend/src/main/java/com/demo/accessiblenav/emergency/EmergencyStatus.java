package com.demo.accessiblenav.emergency;

/**
 * 紧急事件状态
 */
public enum EmergencyStatus {
    ACTIVE("求助中"),
    RESPONDING("响应中"),
    RESOLVED("已解决"),
    CANCELLED("已取消");

    private final String displayName;

    EmergencyStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
