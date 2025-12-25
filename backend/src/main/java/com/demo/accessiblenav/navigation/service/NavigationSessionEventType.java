package com.demo.accessiblenav.navigation.service;

public final class NavigationSessionEventType {
    private NavigationSessionEventType() {}

    public static final String START = "START";
    public static final String PAUSE = "PAUSE";
    public static final String RESUME = "RESUME";
    public static final String END = "END";
    public static final String LOCATION = "LOCATION";
    public static final String REROUTE = "REROUTE";
    public static final String DEVIATION = "DEVIATION";
    public static final String WAYPOINT_REACHED = "WAYPOINT_REACHED";
}

