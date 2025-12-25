package com.demo.accessiblenav.graph;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EdgeRepository extends JpaRepository<EdgeEntity, Long> {

	Optional<EdgeEntity> findByFromNode_IdAndToNode_Id(Long fromNodeId, Long toNodeId);

	@Query("select e from EdgeEntity e join fetch e.fromNode join fetch e.toNode")
	List<EdgeEntity> findAllWithNodes();

	@Query("select e from EdgeEntity e join fetch e.fromNode join fetch e.toNode where e.tenantId = :tenantId")
	List<EdgeEntity> findAllWithNodesByTenant(@Param("tenantId") String tenantId);
}
