package com.demo.accessiblenav.audit.dto;

import java.time.Instant;

public class OperationLogDto {

    private Long id;
    private String actor;
    private String actorRole;
    private String action;
    private String detail;
    private Instant createdAt;

    public OperationLogDto(Long id, String actor, String actorRole, String action, String detail, Instant createdAt) {
        this.id = id;
        this.actor = actor;
        this.actorRole = actorRole;
        this.action = action;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
