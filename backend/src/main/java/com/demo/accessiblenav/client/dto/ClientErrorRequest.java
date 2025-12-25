package com.demo.accessiblenav.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClientErrorRequest {

    @NotBlank
    @Size(max = 32)
    private String type;

    @NotBlank
    @Size(max = 1000)
    private String message;

    @Size(max = 4000)
    private String stack;

    @Size(max = 1000)
    private String url;

    @Size(max = 1000)
    private String meta;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStack() {
        return stack;
    }

    public void setStack(String stack) {
        this.stack = stack;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
    }
}
