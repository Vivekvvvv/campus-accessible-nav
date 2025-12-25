package com.demo.accessiblenav.navigation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NavigationSessionRepository extends JpaRepository<NavigationSessionEntity, String> {
    Optional<NavigationSessionEntity> findFirstByUserIdAndStatusOrderByCreatedAtDesc(String userId, NavigationSessionStatus status);

    Optional<NavigationSessionEntity> findFirstByUserIdAndStatusAndTenantIdOrderByCreatedAtDesc(String userId, NavigationSessionStatus status, String tenantId);

    long countByStatus(NavigationSessionStatus status);

    List<NavigationSessionEntity> findByStatusInAndStartedAtBeforeOrderByStartedAtAsc(List<NavigationSessionStatus> statuses, Instant cutoff, Pageable pageable);

    List<NavigationSessionEntity> findByStatusInAndLastLocationAtBeforeOrderByLastLocationAtAsc(List<NavigationSessionStatus> statuses, Instant cutoff, Pageable pageable);

    Optional<NavigationSessionEntity> findFirstByResumeTokenOrderByCreatedAtDesc(String resumeToken);

    Optional<NavigationSessionEntity> findFirstByResumeTokenAndTenantIdOrderByCreatedAtDesc(String resumeToken, String tenantId);
}
