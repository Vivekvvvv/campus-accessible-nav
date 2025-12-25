package com.demo.accessiblenav.messaging.events;

import com.demo.accessiblenav.messaging.DomainEvent;

/**
 * 路网更新事件
 */
public class GraphUpdatedEvent extends DomainEvent {

    private UpdateType updateType;
    private Long nodeId;
    private Long edgeId;
    private String description;

    public GraphUpdatedEvent(String graphId, UpdateType updateType) {
        super(graphId, "Graph");
        this.updateType = updateType;
    }

    public enum UpdateType {
        NODE_ADDED,
        NODE_REMOVED,
        NODE_UPDATED,
        EDGE_ADDED,
        EDGE_REMOVED,
        EDGE_UPDATED,
        FULL_REBUILD
    }

    // Getters and Setters
    public UpdateType getUpdateType() { return updateType; }
    public void setUpdateType(UpdateType updateType) { this.updateType = updateType; }
    public Long getNodeId() { return nodeId; }
    public void setNodeId(Long nodeId) { this.nodeId = nodeId; }
    public Long getEdgeId() { return edgeId; }
    public void setEdgeId(Long edgeId) { this.edgeId = edgeId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
