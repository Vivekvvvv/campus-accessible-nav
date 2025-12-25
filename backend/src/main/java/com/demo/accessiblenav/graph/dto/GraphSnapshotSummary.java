package com.demo.accessiblenav.graph.dto;

import java.time.Instant;

public class GraphSnapshotSummary {

    private Long id;
    private long version;
    private String label;
    private int nodeCount;
    private int edgeCount;
    private double qualityScore;
    private Instant createdAt;

    public GraphSnapshotSummary(Long id,
                                long version,
                                String label,
                                int nodeCount,
                                int edgeCount,
                                double qualityScore,
                                Instant createdAt) {
        this.id = id;
        this.version = version;
        this.label = label;
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.qualityScore = qualityScore;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public String getLabel() {
        return label;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
