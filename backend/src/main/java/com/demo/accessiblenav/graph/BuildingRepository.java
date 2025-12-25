package com.demo.accessiblenav.graph;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BuildingRepository extends JpaRepository<BuildingEntity, Long> {

    List<BuildingEntity> findAllByOrderByNameAsc();

    List<BuildingEntity> findAllByTenantIdOrderByNameAsc(String tenantId);
}
