package com.demo.accessiblenav.emergency;

/**
 * 联系人关系类型
 */
public enum ContactRelationship {
    FAMILY("家人"),
    FRIEND("朋友"),
    GUARDIAN("监护人"),
    CAREGIVER("护理人员"),
    OTHER("其他");

    private final String displayName;

    ContactRelationship(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
