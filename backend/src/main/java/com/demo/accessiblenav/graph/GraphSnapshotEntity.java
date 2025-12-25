package com.demo.accessiblenav.graph;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "t_graph_snapshot",
        indexes = {
                @Index(name = "idx_graph_snapshot_created", columnList = "created_at"),
                @Index(name = "idx_graph_snapshot_version", columnList = "version")
        }
)
public class GraphSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long version;

    @Column(nullable = false, length = 64)
    private String label;

    @Column(name = "node_count", nullable = false)
    private int nodeCount;

    @Column(name = "edge_count", nullable = false)
    private int edgeCount;

    @Column(name = "quality_score", nullable = false)
    private double qualityScore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    public Long getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public void setEdgeCount(int edgeCount) {
        this.edgeCount = edgeCount;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(double qualityScore) {
        this.qualityScore = qualityScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }
}
