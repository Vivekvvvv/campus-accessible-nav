package com.demo.accessiblenav.favorites;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FavoritePlaceRepository extends JpaRepository<FavoritePlaceEntity, Long> {
    List<FavoritePlaceEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
