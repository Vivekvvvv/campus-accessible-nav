package com.demo.accessiblenav.obstacle.dto;

import jakarta.validation.constraints.NotNull;

public class EdgeDisableRequest {

    @NotNull
    private Long edgeId;

    private String reason;

    public Long getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(Long edgeId) {
        this.edgeId = edgeId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
