package com.demo.accessiblenav.graph.dto;

public class GraphRepairSummary {

    private boolean dryRun;
    private double maxConnectMeters;
    private int danglingEdgesRemoved;
    private int duplicateEdgesRemoved;
    private int isolatedNodesConnected;
    private int disconnectedComponentsConnected;
    private int newEdgesCreated;
    private int isolatedNodesRemaining;
    private int disconnectedComponentsRemaining;

    public GraphRepairSummary(boolean dryRun,
                              double maxConnectMeters,
                              int danglingEdgesRemoved,
                              int duplicateEdgesRemoved,
                              int isolatedNodesConnected,
                              int disconnectedComponentsConnected,
                              int newEdgesCreated,
                              int isolatedNodesRemaining,
                              int disconnectedComponentsRemaining) {
        this.dryRun = dryRun;
        this.maxConnectMeters = maxConnectMeters;
        this.danglingEdgesRemoved = danglingEdgesRemoved;
        this.duplicateEdgesRemoved = duplicateEdgesRemoved;
        this.isolatedNodesConnected = isolatedNodesConnected;
        this.disconnectedComponentsConnected = disconnectedComponentsConnected;
        this.newEdgesCreated = newEdgesCreated;
        this.isolatedNodesRemaining = isolatedNodesRemaining;
        this.disconnectedComponentsRemaining = disconnectedComponentsRemaining;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public double getMaxConnectMeters() {
        return maxConnectMeters;
    }

    public int getDanglingEdgesRemoved() {
        return danglingEdgesRemoved;
    }

    public int getDuplicateEdgesRemoved() {
        return duplicateEdgesRemoved;
    }

    public int getIsolatedNodesConnected() {
        return isolatedNodesConnected;
    }

    public int getDisconnectedComponentsConnected() {
        return disconnectedComponentsConnected;
    }

    public int getNewEdgesCreated() {
        return newEdgesCreated;
    }

    public int getIsolatedNodesRemaining() {
        return isolatedNodesRemaining;
    }

    public int getDisconnectedComponentsRemaining() {
        return disconnectedComponentsRemaining;
    }
}
