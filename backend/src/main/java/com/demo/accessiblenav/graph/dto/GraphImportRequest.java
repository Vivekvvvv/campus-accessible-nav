package com.demo.accessiblenav.graph.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class GraphImportRequest {

    @NotEmpty
    @Valid
    private List<NodeUpsert> nodes;

    @NotEmpty
    @Valid
    private List<EdgeCreate> edges;

    public List<NodeUpsert> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeUpsert> nodes) {
        this.nodes = nodes;
    }

    public List<EdgeCreate> getEdges() {
        return edges;
    }

    public void setEdges(List<EdgeCreate> edges) {
        this.edges = edges;
    }

    public static class NodeUpsert {
        @NotNull
        private String key;

        @NotNull
        private String lat;

        @NotNull
        private String lng;

        @NotNull
        private String nodeType;

        private String extraJson;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLat() {
            return lat;
        }

        public void setLat(String lat) {
            this.lat = lat;
        }

        public String getLng() {
            return lng;
        }

        public void setLng(String lng) {
            this.lng = lng;
        }

        public String getNodeType() {
            return nodeType;
        }

        public void setNodeType(String nodeType) {
            this.nodeType = nodeType;
        }

        public String getExtraJson() {
            return extraJson;
        }

        public void setExtraJson(String extraJson) {
            this.extraJson = extraJson;
        }
    }

    public static class EdgeCreate {
        @NotNull
        private String fromKey;

        @NotNull
        private String toKey;

        @NotNull
        private Double distanceM;

        private Boolean oneway;
        private Boolean hasStairs;
        private Integer slopeLevel;
        private Boolean accessibleDefault;
        private Double baseCost;

        public String getFromKey() {
            return fromKey;
        }

        public void setFromKey(String fromKey) {
            this.fromKey = fromKey;
        }

        public String getToKey() {
            return toKey;
        }

        public void setToKey(String toKey) {
            this.toKey = toKey;
        }

        public Double getDistanceM() {
            return distanceM;
        }

        public void setDistanceM(Double distanceM) {
            this.distanceM = distanceM;
        }

        public Boolean getOneway() {
            return oneway;
        }

        public void setOneway(Boolean oneway) {
            this.oneway = oneway;
        }

        public Boolean getHasStairs() {
            return hasStairs;
        }

        public void setHasStairs(Boolean hasStairs) {
            this.hasStairs = hasStairs;
        }

        public Integer getSlopeLevel() {
            return slopeLevel;
        }

        public void setSlopeLevel(Integer slopeLevel) {
            this.slopeLevel = slopeLevel;
        }

        public Boolean getAccessibleDefault() {
            return accessibleDefault;
        }

        public void setAccessibleDefault(Boolean accessibleDefault) {
            this.accessibleDefault = accessibleDefault;
        }

        public Double getBaseCost() {
            return baseCost;
        }

        public void setBaseCost(Double baseCost) {
            this.baseCost = baseCost;
        }
    }
}
