package com.demo.accessiblenav.profile;

import com.demo.accessiblenav.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/profile")
public class AccessibilityProfileController {

    private final AccessibilityProfileService service;

    public AccessibilityProfileController(AccessibilityProfileService service) {
        this.service = service;
    }

    @GetMapping("/accessibility")
    public ResponseEntity<ApiResponse<AccessibilityProfileDto>> getProfile(Principal principal) {
        String userId = requireUserId(principal);
        AccessibilityProfileDto profile = service.getUserProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    @PutMapping("/accessibility")
    public ResponseEntity<ApiResponse<AccessibilityProfileDto>> updateProfile(
            @Valid @RequestBody AccessibilityProfileDto request,
            Principal principal) {
        String userId = requireUserId(principal);
        AccessibilityProfileDto profile = service.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Accessibility profile updated", profile));
    }

    private static String requireUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new IllegalStateException("missing authenticated user");
        }
        return principal.getName();
    }
}
