package com.demo.accessiblenav.events;

import java.time.Instant;
import java.util.Map;

public class SseEvent {

    private String type;
    private Instant at;
    private Map<String, Object> data;

    public SseEvent(String type, Instant at, Map<String, Object> data) {
        this.type = type;
        this.at = at;
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public Instant getAt() {
        return at;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
