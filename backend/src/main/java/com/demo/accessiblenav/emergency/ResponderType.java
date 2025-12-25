package com.demo.accessiblenav.emergency;

/**
 * 响应者类型
 */
public enum ResponderType {
    SECURITY("安保人员"),
    VOLUNTEER("志愿者"),
    CONTACT("紧急联系人");

    private final String displayName;

    ResponderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
