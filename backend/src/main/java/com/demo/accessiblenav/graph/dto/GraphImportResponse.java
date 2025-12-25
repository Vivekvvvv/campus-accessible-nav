package com.demo.accessiblenav.graph.dto;

public class GraphImportResponse {

    private int nodesUpserted;
    private int edgesCreated;
    private GraphValidationReport report;

    public GraphImportResponse(int nodesUpserted, int edgesCreated) {
        this(nodesUpserted, edgesCreated, null);
    }

    public GraphImportResponse(int nodesUpserted, int edgesCreated, GraphValidationReport report) {
        this.nodesUpserted = nodesUpserted;
        this.edgesCreated = edgesCreated;
        this.report = report;
    }

    public int getNodesUpserted() {
        return nodesUpserted;
    }

    public int getEdgesCreated() {
        return edgesCreated;
    }

    public GraphValidationReport getReport() {
        return report;
    }
}
