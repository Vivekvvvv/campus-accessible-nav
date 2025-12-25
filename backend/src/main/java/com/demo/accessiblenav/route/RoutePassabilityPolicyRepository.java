package com.demo.accessiblenav.route;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoutePassabilityPolicyRepository extends JpaRepository<RoutePassabilityPolicyEntity, Long> {

    Optional<RoutePassabilityPolicyEntity> findByTenantId(String tenantId);
}
