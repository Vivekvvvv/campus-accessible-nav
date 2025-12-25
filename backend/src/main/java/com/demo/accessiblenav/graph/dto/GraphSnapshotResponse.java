package com.demo.accessiblenav.graph.dto;

import java.util.List;
import java.time.Instant;
import java.util.Collections;

public class GraphSnapshotResponse {

    private List<NodeDto> nodes;
    private List<EdgeDto> edges;

    public GraphSnapshotResponse() {
        this.nodes = Collections.emptyList();
        this.edges = Collections.emptyList();
    }

    public GraphSnapshotResponse(List<NodeDto> nodes, List<EdgeDto> edges) {
        this.nodes = nodes;
        this.edges = edges;
    }

    public List<NodeDto> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeDto> nodes) {
        this.nodes = nodes;
    }

    public List<EdgeDto> getEdges() {
        return edges;
    }

    public void setEdges(List<EdgeDto> edges) {
        this.edges = edges;
    }

    public static class NodeDto {
        private Long id;
        private String lat;
        private String lng;
        private String nodeType;
        private String extraJson;

        public NodeDto(Long id, String lat, String lng, String nodeType, String extraJson) {
            this.id = id;
            this.lat = lat;
            this.lng = lng;
            this.nodeType = nodeType;
            this.extraJson = extraJson;
        }

        public Long getId() {
            return id;
        }

        public String getLat() {
            return lat;
        }

        public String getLng() {
            return lng;
        }

        public String getNodeType() {
            return nodeType;
        }

        public String getExtraJson() {
            return extraJson;
        }
    }

    public static class EdgeDto {
        private Long id;
        private Long fromNodeId;
        private Long toNodeId;
        private String fromLat;
        private String fromLng;
        private String toLat;
        private String toLng;
        private double distanceM;
        private boolean oneway;
        private boolean hasStairs;
        private int slopeLevel;
        private boolean accessibleDefault;
        private double baseCost;
        private boolean disabled;
        private String disabledReason;
        private Instant disabledEndAt;

        public EdgeDto(Long id,
                       Long fromNodeId,
                       Long toNodeId,
                       String fromLat,
                       String fromLng,
                       String toLat,
                       String toLng,
                       double distanceM,
                       boolean oneway,
                       boolean hasStairs,
                       int slopeLevel,
                       boolean accessibleDefault,
                       double baseCost,
                       boolean disabled,
                       String disabledReason,
                       Instant disabledEndAt) {
            this.id = id;
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
            this.fromLat = fromLat;
            this.fromLng = fromLng;
            this.toLat = toLat;
            this.toLng = toLng;
            this.distanceM = distanceM;
            this.oneway = oneway;
            this.hasStairs = hasStairs;
            this.slopeLevel = slopeLevel;
            this.accessibleDefault = accessibleDefault;
            this.baseCost = baseCost;
            this.disabled = disabled;
            this.disabledReason = disabledReason;
            this.disabledEndAt = disabledEndAt;
        }

        public Long getId() {
            return id;
        }

        public Long getFromNodeId() {
            return fromNodeId;
        }

        public Long getToNodeId() {
            return toNodeId;
        }

        public String getFromLat() {
            return fromLat;
        }

        public String getFromLng() {
            return fromLng;
        }

        public String getToLat() {
            return toLat;
        }

        public String getToLng() {
            return toLng;
        }

        public double getDistanceM() {
            return distanceM;
        }

        public boolean isOneway() {
            return oneway;
        }

        public boolean isHasStairs() {
            return hasStairs;
        }

        public int getSlopeLevel() {
            return slopeLevel;
        }

        public boolean isAccessibleDefault() {
            return accessibleDefault;
        }

        public double getBaseCost() {
            return baseCost;
        }

        public boolean isDisabled() {
            return disabled;
        }

        public String getDisabledReason() {
            return disabledReason;
        }

        public Instant getDisabledEndAt() {
            return disabledEndAt;
        }
    }
}
