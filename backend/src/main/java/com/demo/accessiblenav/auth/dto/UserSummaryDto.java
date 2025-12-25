package com.demo.accessiblenav.auth.dto;

import com.demo.accessiblenav.auth.UserRole;

import java.time.Instant;

public class UserSummaryDto {

    private Long id;
    private String username;
    private UserRole role;
    private Instant createdAt;

    public UserSummaryDto(Long id, String username, UserRole role, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
