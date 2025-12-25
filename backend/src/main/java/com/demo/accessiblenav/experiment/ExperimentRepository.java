package com.demo.accessiblenav.experiment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExperimentRepository extends JpaRepository<ExperimentEntity, Long> {

    Optional<ExperimentEntity> findByName(String name);

    List<ExperimentEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<ExperimentEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
