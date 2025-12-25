package com.demo.accessiblenav.obstacle.dto;

public class EdgeDisableResponse {

    private Long edgeId;
    private boolean disabled;

    public EdgeDisableResponse(Long edgeId, boolean disabled) {
        this.edgeId = edgeId;
        this.disabled = disabled;
    }

    public Long getEdgeId() {
        return edgeId;
    }

    public boolean isDisabled() {
        return disabled;
    }
}
