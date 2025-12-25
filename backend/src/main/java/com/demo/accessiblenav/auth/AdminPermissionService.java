package com.demo.accessiblenav.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@Service
public class AdminPermissionService {

    public UserRole currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String authority = ga == null ? null : ga.getAuthority();
            if (authority == null) {
                continue;
            }
            if (authority.startsWith("ROLE_")) {
                String name = authority.substring(5);
                try {
                    return UserRole.valueOf(name);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return "";
        }
        return auth.getName();
    }

    public boolean hasAny(UserRole... roles) {
        UserRole current = currentRole();
        if (current == null || roles == null) {
            return false;
        }
        return Arrays.asList(roles).contains(current);
    }

    public void requireAny(UserRole... roles) {
        if (!hasAny(roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
        }
    }
}
