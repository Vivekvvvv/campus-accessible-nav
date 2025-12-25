package com.demo.accessiblenav.graph.dto;

public class GraphIssuePoint {

    private Long nodeId;
    private String type;
    private double lat;
    private double lng;

    public GraphIssuePoint(Long nodeId, String type, double lat, double lng) {
        this.nodeId = nodeId;
        this.type = type;
        this.lat = lat;
        this.lng = lng;
    }

    public Long getNodeId() {
        return nodeId;
    }

    public String getType() {
        return type;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }
}
