package com.demo.accessiblenav.facility;

/**
 * 无障碍设施类型
 */
public enum FacilityType {
    ELEVATOR("电梯"),
    RAMP("坡道"),
    ACCESSIBLE_TOILET("无障碍卫生间"),
    BRAILLE_SIGN("盲文标识"),
    AUDIO_SIGNAL("音响信号"),
    WHEELCHAIR_CHARGING("轮椅充电站"),
    ACCESSIBLE_PARKING("无障碍停车位"),
    TACTILE_PAVING("盲道"),
    HANDRAIL("扶手"),
    AUTO_DOOR("自动门"),
    SERVICE_DESK("服务台"),
    REST_AREA("休息区");

    private final String displayName;

    FacilityType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
