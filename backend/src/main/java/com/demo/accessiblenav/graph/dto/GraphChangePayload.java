package com.demo.accessiblenav.graph.dto;

import jakarta.validation.Valid;
import java.util.List;

public class GraphChangePayload {

    @Valid
    private List<GraphImportRequest.NodeUpsert> nodes;

    @Valid
    private List<GraphImportRequest.EdgeCreate> edges;

    public List<GraphImportRequest.NodeUpsert> getNodes() {
        return nodes;
    }

    public void setNodes(List<GraphImportRequest.NodeUpsert> nodes) {
        this.nodes = nodes;
    }

    public List<GraphImportRequest.EdgeCreate> getEdges() {
        return edges;
    }

    public void setEdges(List<GraphImportRequest.EdgeCreate> edges) {
        this.edges = edges;
    }
}
