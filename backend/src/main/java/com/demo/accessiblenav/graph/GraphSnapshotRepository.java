package com.demo.accessiblenav.graph;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GraphSnapshotRepository extends JpaRepository<GraphSnapshotEntity, Long> {

    List<GraphSnapshotEntity> findAllByOrderByCreatedAtDesc();
}
