package com.demo.accessiblenav.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    private final String code;
    private final String message;
    private final String traceId;
    private final int status;
    private final String path;
    private final Instant timestamp;
    private final Object details;

    public ApiErrorResponse(String code, String message, String traceId, int status, String path, Instant timestamp, Object details) {
        this.code = code;
        this.message = message;
        this.traceId = traceId;
        this.status = status;
        this.path = path;
        this.timestamp = timestamp;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public int getStatus() {
        return status;
    }

    public String getPath() {
        return path;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Object getDetails() {
        return details;
    }
}
