package com.demo.accessiblenav.emergency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyBroadcastRepository extends JpaRepository<EmergencyBroadcastEntity, Long> {

    List<EmergencyBroadcastEntity> findTop100ByTenantIdOrderByCreatedAtDesc(String tenantId);
}
