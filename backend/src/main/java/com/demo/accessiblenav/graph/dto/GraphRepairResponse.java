package com.demo.accessiblenav.graph.dto;

public class GraphRepairResponse {

    private GraphValidationReport before;
    private GraphValidationReport after;
    private GraphRepairSummary summary;

    public GraphRepairResponse(GraphValidationReport before,
                               GraphValidationReport after,
                               GraphRepairSummary summary) {
        this.before = before;
        this.after = after;
        this.summary = summary;
    }

    public GraphValidationReport getBefore() {
        return before;
    }

    public GraphValidationReport getAfter() {
        return after;
    }

    public GraphRepairSummary getSummary() {
        return summary;
    }
}
