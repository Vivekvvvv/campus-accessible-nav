package com.demo.accessiblenav.experiment.dto;

public class ExperimentAssignmentResponse {
    private String experimentName;
    private String variant;

    public ExperimentAssignmentResponse() {
    }

    public ExperimentAssignmentResponse(String experimentName, String variant) {
        this.experimentName = experimentName;
        this.variant = variant;
    }

    public String getExperimentName() {
        return experimentName;
    }

    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }

    public String getVariant() {
        return variant;
    }

    public void setVariant(String variant) {
        this.variant = variant;
    }
}
