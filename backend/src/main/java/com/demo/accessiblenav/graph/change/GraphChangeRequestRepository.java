package com.demo.accessiblenav.graph.change;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GraphChangeRequestRepository extends JpaRepository<GraphChangeRequestEntity, Long> {

    List<GraphChangeRequestEntity> findAllByOrderByCreatedAtDesc();
}
