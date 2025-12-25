package com.demo.accessiblenav.profile;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;

@Service
public class AccessibilityProfileService {

    private final AccessibilityProfileRepository repository;

    public AccessibilityProfileService(AccessibilityProfileRepository repository) {
        this.repository = repository;
    }

    public AccessibilityProfileDto getUserProfile(String userId) {
        AccessibilityProfileEntity entity = repository.findByUserId(userId)
                .orElseGet(() -> createDefaultProfile(userId));
        return toDto(entity);
    }

    @Transactional
    public AccessibilityProfileDto updateProfile(String userId, AccessibilityProfileDto dto) {
        AccessibilityProfileEntity entity = repository.findByUserId(userId)
                .orElseGet(() -> {
                    AccessibilityProfileEntity created = new AccessibilityProfileEntity();
                    created.setUserId(userId);
                    return created;
                });

        if (dto.getMobilityMode() != null) {
            entity.setMobilityMode(normalizeMode(dto.getMobilityMode()));
        }
        if (dto.getAvoidStairs() != null) {
            entity.setAvoidStairs(dto.getAvoidStairs());
        }
        if (dto.getAvoidSlope() != null) {
            entity.setAvoidSlope(dto.getAvoidSlope());
        }
        if (dto.getAvoidConstruction() != null) {
            entity.setAvoidConstruction(dto.getAvoidConstruction());
        }
        if (dto.getMaxSlopePercent() != null) {
            entity.setMaxSlopePercent(dto.getMaxSlopePercent());
        }

        entity = Objects.requireNonNull(repository.save(Objects.requireNonNull(entity)));
        return toDto(entity);
    }

    private AccessibilityProfileEntity createDefaultProfile(String userId) {
        AccessibilityProfileEntity entity = new AccessibilityProfileEntity();
        entity.setUserId(userId);
        return repository.save(entity);
    }

    private static String normalizeMode(String raw) {
        String mode = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (mode.isEmpty()) {
            return "WALK";
        }
        return switch (mode) {
            case "WALK", "WHEELCHAIR", "VISUAL_IMPAIRMENT", "STROLLER" -> mode;
            default -> throw new IllegalArgumentException("Unsupported mobilityMode: " + raw);
        };
    }

    private AccessibilityProfileDto toDto(AccessibilityProfileEntity entity) {
        AccessibilityProfileDto dto = new AccessibilityProfileDto();
        dto.setMobilityMode(entity.getMobilityMode());
        dto.setAvoidStairs(entity.getAvoidStairs());
        dto.setAvoidSlope(entity.getAvoidSlope());
        dto.setAvoidConstruction(entity.getAvoidConstruction());
        dto.setMaxSlopePercent(entity.getMaxSlopePercent());
        return dto;
    }
}
