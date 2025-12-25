package com.demo.accessiblenav.graph.dto;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 用于“画布即真值”的全量覆盖写入：允许空数组（表示清空路网）。
 */
public class GraphReplaceRequest {

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
