package com.demo.accessiblenav.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 紧急联系人仓库
 */
@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContactEntity, Long> {

    /**
     * 根据用户ID查找所有联系人
     */
    List<EmergencyContactEntity> findByUserIdOrderByIsPrimaryDesc(String userId);

    List<EmergencyContactEntity> findByUserIdAndTenantIdOrderByIsPrimaryDesc(String userId, String tenantId);

    /**
     * 根据用户ID查找主要联系人
     */
    List<EmergencyContactEntity> findByUserIdAndIsPrimaryTrue(String userId);

    List<EmergencyContactEntity> findByUserIdAndIsPrimaryTrueAndTenantId(String userId, String tenantId);

    /**
     * 检查用户是否已添加该联系人
     */
    boolean existsByUserIdAndPhoneNumber(String userId, String phoneNumber);

    boolean existsByUserIdAndPhoneNumberAndTenantId(String userId, String phoneNumber, String tenantId);

    /**
     * 删除用户的所有联系人
     */
    void deleteByUserId(String userId);
}
