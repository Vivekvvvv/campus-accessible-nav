package com.demo.accessiblenav.graph;

import jakarta.persistence.*;

@Entity
@Table(
        name = "t_edge",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_edge_from_to", columnNames = {"from_node_id", "to_node_id"})
    },
        indexes = {
                @Index(name = "idx_edge_from", columnList = "from_node_id"),
                @Index(name = "idx_edge_to", columnList = "to_node_id")
        }
)
public class EdgeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    private NodeEntity fromNode;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    private NodeEntity toNode;

    @Column(name = "distance_m", nullable = false)
    private double distanceM;

    @Column(name = "is_oneway", nullable = false)
    private boolean oneway;

    @Column(name = "has_stairs", nullable = false)
    private boolean hasStairs;

    @Column(name = "is_elevator", nullable = false)
    private boolean isElevator;

    @Column(name = "slope_level", nullable = false)
    private int slopeLevel;

    @Column(name = "is_accessible_default", nullable = false)
    private boolean accessibleDefault;

    @Column(name = "base_cost", nullable = false)
    private double baseCost;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId = "default";

    @Column(name = "passability_probability", nullable = false)
    private double passabilityProbability = 1.0;

    public Long getId() {
        return id;
    }

    public NodeEntity getFromNode() {
        return fromNode;
    }

    public void setFromNode(NodeEntity fromNode) {
        this.fromNode = fromNode;
    }

    public NodeEntity getToNode() {
        return toNode;
    }

    public void setToNode(NodeEntity toNode) {
        this.toNode = toNode;
    }

    public double getDistanceM() {
        return distanceM;
    }

    public void setDistanceM(double distanceM) {
        this.distanceM = distanceM;
    }

    public boolean isOneway() {
        return oneway;
    }

    public void setOneway(boolean oneway) {
        this.oneway = oneway;
    }

    public boolean isHasStairs() {
        return hasStairs;
    }

    public void setHasStairs(boolean hasStairs) {
        this.hasStairs = hasStairs;
    }

    public boolean isElevator() {
        return isElevator;
    }

    public void setElevator(boolean elevator) {
        isElevator = elevator;
    }

    public int getSlopeLevel() {
        return slopeLevel;
    }

    public void setSlopeLevel(int slopeLevel) {
        this.slopeLevel = slopeLevel;
    }

    public boolean isAccessibleDefault() {
        return accessibleDefault;
    }

    public void setAccessibleDefault(boolean accessibleDefault) {
        this.accessibleDefault = accessibleDefault;
    }

    public double getBaseCost() {
        return baseCost;
    }

    public void setBaseCost(double baseCost) {
        this.baseCost = baseCost;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public double getPassabilityProbability() {
        return passabilityProbability;
    }

    public void setPassabilityProbability(double passabilityProbability) {
        this.passabilityProbability = passabilityProbability;
    }
}
