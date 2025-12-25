package com.demo.accessiblenav.emergency;

/**
 * 紧急事件类型
 */
public enum EmergencyType {
    FALL("跌倒"),
    LOST("迷路"),
    MEDICAL("身体不适"),
    OTHER("其他");

    private final String displayName;

    EmergencyType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
