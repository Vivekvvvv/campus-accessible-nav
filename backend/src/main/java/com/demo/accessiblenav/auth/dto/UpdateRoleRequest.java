package com.demo.accessiblenav.auth.dto;

import com.demo.accessiblenav.auth.UserRole;

import jakarta.validation.constraints.NotNull;

public class UpdateRoleRequest {

    @NotNull
    private UserRole role;

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }
}
