package com.demo.accessiblenav.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 志愿者仓库
 */
@Repository
public interface VolunteerRepository extends JpaRepository<VolunteerEntity, Long> {

    /**
     * 根据用户ID查找志愿者
     */
    Optional<VolunteerEntity> findByUserId(String userId);

    Optional<VolunteerEntity> findByUserIdAndTenantId(String userId, String tenantId);

    /**
     * 查找所有活跃的志愿者
     */
    List<VolunteerEntity> findByIsActiveTrue();

    List<VolunteerEntity> findByIsActiveTrueAndTenantId(String tenantId);

    /**
     * 查找指定范围内的活跃志愿者
     */
    @Query("SELECT v FROM VolunteerEntity v WHERE v.isActive = true " +
           "AND v.lastLocationLat IS NOT NULL " +
           "AND v.lastLocationLat BETWEEN :minLat AND :maxLat " +
           "AND v.lastLocationLng BETWEEN :minLng AND :maxLng")
    List<VolunteerEntity> findNearbyActiveVolunteers(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng);

    @Query("SELECT v FROM VolunteerEntity v WHERE v.isActive = true " +
            "AND v.tenantId = :tenantId " +
            "AND v.lastLocationLat IS NOT NULL " +
            "AND v.lastLocationLat BETWEEN :minLat AND :maxLat " +
            "AND v.lastLocationLng BETWEEN :minLng AND :maxLng")
    List<VolunteerEntity> findNearbyActiveVolunteersByTenant(
            @Param("tenantId") String tenantId,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng);

    /**
     * 检查用户是否已是志愿者
     */
    boolean existsByUserId(String userId);

    boolean existsByUserIdAndTenantId(String userId, String tenantId);
}
