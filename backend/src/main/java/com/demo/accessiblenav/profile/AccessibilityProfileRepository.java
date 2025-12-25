package com.demo.accessiblenav.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccessibilityProfileRepository extends JpaRepository<AccessibilityProfileEntity, Long> {

    Optional<AccessibilityProfileEntity> findByUserId(String userId);
}
