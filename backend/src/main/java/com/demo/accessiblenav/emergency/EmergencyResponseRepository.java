package com.demo.accessiblenav.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 紧急事件响应仓库
 */
@Repository
public interface EmergencyResponseRepository extends JpaRepository<EmergencyResponseEntity, Long> {

    /**
     * 根据事件ID查找所有响应记录
     */
    List<EmergencyResponseEntity> findByEventIdOrderByResponseTimeDesc(Long eventId);

    List<EmergencyResponseEntity> findByEventIdAndTenantIdOrderByResponseTimeDesc(Long eventId, String tenantId);

    /**
     * 根据响应者ID查找响应记录
     */
    List<EmergencyResponseEntity> findByResponderIdOrderByResponseTimeDesc(String responderId);

    List<EmergencyResponseEntity> findByResponderIdAndTenantIdOrderByResponseTimeDesc(String responderId, String tenantId);

    /**
     * 检查响应者是否已响应该事件
     */
    boolean existsByEventIdAndResponderId(Long eventId, String responderId);

    boolean existsByEventIdAndResponderIdAndTenantId(Long eventId, String responderId, String tenantId);
}
