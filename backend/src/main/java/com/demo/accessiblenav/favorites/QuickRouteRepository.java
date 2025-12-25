package com.demo.accessiblenav.favorites;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuickRouteRepository extends JpaRepository<QuickRouteEntity, Long> {
    List<QuickRouteEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
}
