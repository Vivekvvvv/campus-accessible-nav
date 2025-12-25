package com.demo.accessiblenav.graph.dto;

import com.demo.accessiblenav.graph.change.GraphChangeKind;
import com.demo.accessiblenav.graph.change.GraphChangePayloadType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public class GraphChangeRequestCreateRequest {

    @NotNull
    private GraphChangeKind kind;

    @NotNull
    private GraphChangePayloadType payloadType;

    @NotNull
    @Valid
    private GraphChangePayload payload;

    private String note;

    public GraphChangeKind getKind() {
        return kind;
    }

    public void setKind(GraphChangeKind kind) {
        this.kind = kind;
    }

    public GraphChangePayloadType getPayloadType() {
        return payloadType;
    }

    public void setPayloadType(GraphChangePayloadType payloadType) {
        this.payloadType = payloadType;
    }

    public GraphChangePayload getPayload() {
        return payload;
    }

    public void setPayload(GraphChangePayload payload) {
        this.payload = payload;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
