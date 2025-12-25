package com.demo.accessiblenav.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 紧急事件仓库
 */
@Repository
public interface EmergencyEventRepository extends JpaRepository<EmergencyEventEntity, Long> {

    /**
     * 根据用户ID查找活跃的紧急事件
     */
    List<EmergencyEventEntity> findByUserIdAndStatusIn(String userId, List<EmergencyStatus> statuses);

    /**
     * 查找所有活跃的紧急事件
     */
    List<EmergencyEventEntity> findByStatusOrderByCreatedAtDesc(EmergencyStatus status);

    /**
     * 查找所有活跃和响应中的紧急事件
     */
    List<EmergencyEventEntity> findByStatusInOrderByCreatedAtDesc(List<EmergencyStatus> statuses);

    List<EmergencyEventEntity> findByStatusInAndTenantIdOrderByCreatedAtDesc(List<EmergencyStatus> statuses, String tenantId);

    /**
     * 根据用户ID查找最近的紧急事件
     */
    List<EmergencyEventEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    List<EmergencyEventEntity> findByUserIdAndTenantIdOrderByCreatedAtDesc(String userId, String tenantId);

    /**
     * 查找指定范围内的活跃紧急事件（简化版距离计算）
     */
    @Query("SELECT e FROM EmergencyEventEntity e WHERE e.status = :status " +
           "AND e.lat BETWEEN :minLat AND :maxLat " +
           "AND e.lng BETWEEN :minLng AND :maxLng")
    List<EmergencyEventEntity> findNearbyActiveEvents(
            @Param("status") EmergencyStatus status,
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng);
}
