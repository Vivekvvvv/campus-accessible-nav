package com.demo.accessiblenav.favorites;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FavoriteGroupRepository extends JpaRepository<FavoriteGroupEntity, Long> {
    List<FavoriteGroupEntity> findByUserIdOrderBySortOrderAscNameAsc(String userId);
}
