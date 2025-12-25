package com.demo.accessiblenav.experiment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExperimentAssignmentRepository extends JpaRepository<ExperimentAssignmentEntity, Long> {

    Optional<ExperimentAssignmentEntity> findByExperimentAndUserId(ExperimentEntity experiment, String userId);
}
