package com.demo.accessiblenav.graph.dto;

public class GraphRepairRequest {

    private Boolean dryRun;
    private Double maxConnectMeters;
    private Boolean connectIsolated;
    private Boolean connectDisconnected;
    private Boolean removeDanglingEdges;
    private Boolean removeDuplicateEdges;

    public Boolean getDryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Double getMaxConnectMeters() {
        return maxConnectMeters;
    }

    public void setMaxConnectMeters(Double maxConnectMeters) {
        this.maxConnectMeters = maxConnectMeters;
    }

    public Boolean getConnectIsolated() {
        return connectIsolated;
    }

    public void setConnectIsolated(Boolean connectIsolated) {
        this.connectIsolated = connectIsolated;
    }

    public Boolean getConnectDisconnected() {
        return connectDisconnected;
    }

    public void setConnectDisconnected(Boolean connectDisconnected) {
        this.connectDisconnected = connectDisconnected;
    }

    public Boolean getRemoveDanglingEdges() {
        return removeDanglingEdges;
    }

    public void setRemoveDanglingEdges(Boolean removeDanglingEdges) {
        this.removeDanglingEdges = removeDanglingEdges;
    }

    public Boolean getRemoveDuplicateEdges() {
        return removeDuplicateEdges;
    }

    public void setRemoveDuplicateEdges(Boolean removeDuplicateEdges) {
        this.removeDuplicateEdges = removeDuplicateEdges;
    }
}
