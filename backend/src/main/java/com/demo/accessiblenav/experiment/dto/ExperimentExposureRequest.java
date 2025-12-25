package com.demo.accessiblenav.experiment.dto;

public class ExperimentExposureRequest {
    private String userId;
    private String event;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }
}
