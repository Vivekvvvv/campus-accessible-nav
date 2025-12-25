package com.demo.accessiblenav.graph.dto;

import java.util.List;

public class GraphValidationReport {

    private int nodeCount;
    private int edgeCount;
    private int undirectedEdgeCount;
    private int componentCount;
    private int largestComponentNodes;
    private int disconnectedNodes;
    private int isolatedNodes;
    private int deadEndNodes;
    private int danglingEdgeCount;
    private int duplicateEdgeCount;
    private List<Long> isolatedSample;
    private List<Long> disconnectedSample;
    private List<Long> danglingEdgeSample;
    private List<Long> duplicateEdgeSample;
    private double qualityScore;
    private List<GraphIssuePoint> issuePoints;
    private List<String> suggestions;

    public GraphValidationReport(int nodeCount,
                                 int edgeCount,
                                 int undirectedEdgeCount,
                                 int componentCount,
                                 int largestComponentNodes,
                                 int disconnectedNodes,
                                 int isolatedNodes,
                                 int deadEndNodes,
                                 int danglingEdgeCount,
                                 int duplicateEdgeCount,
                                 List<Long> isolatedSample,
                                 List<Long> disconnectedSample,
                                 List<Long> danglingEdgeSample,
                                 List<Long> duplicateEdgeSample,
                                 double qualityScore,
                                 List<GraphIssuePoint> issuePoints,
                                 List<String> suggestions) {
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.undirectedEdgeCount = undirectedEdgeCount;
        this.componentCount = componentCount;
        this.largestComponentNodes = largestComponentNodes;
        this.disconnectedNodes = disconnectedNodes;
        this.isolatedNodes = isolatedNodes;
        this.deadEndNodes = deadEndNodes;
        this.danglingEdgeCount = danglingEdgeCount;
        this.duplicateEdgeCount = duplicateEdgeCount;
        this.isolatedSample = isolatedSample;
        this.disconnectedSample = disconnectedSample;
        this.danglingEdgeSample = danglingEdgeSample;
        this.duplicateEdgeSample = duplicateEdgeSample;
        this.qualityScore = qualityScore;
        this.issuePoints = issuePoints;
        this.suggestions = suggestions;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public int getUndirectedEdgeCount() {
        return undirectedEdgeCount;
    }

    public int getComponentCount() {
        return componentCount;
    }

    public int getLargestComponentNodes() {
        return largestComponentNodes;
    }

    public int getDisconnectedNodes() {
        return disconnectedNodes;
    }

    public int getIsolatedNodes() {
        return isolatedNodes;
    }

    public int getDeadEndNodes() {
        return deadEndNodes;
    }

    public int getDanglingEdgeCount() {
        return danglingEdgeCount;
    }

    public int getDuplicateEdgeCount() {
        return duplicateEdgeCount;
    }

    public List<Long> getIsolatedSample() {
        return isolatedSample;
    }

    public List<Long> getDisconnectedSample() {
        return disconnectedSample;
    }

    public List<Long> getDanglingEdgeSample() {
        return danglingEdgeSample;
    }

    public List<Long> getDuplicateEdgeSample() {
        return duplicateEdgeSample;
    }

    public double getQualityScore() {
        return qualityScore;
    }

    public List<GraphIssuePoint> getIssuePoints() {
        return issuePoints;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }
}
