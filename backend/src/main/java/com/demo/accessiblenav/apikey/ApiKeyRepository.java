package com.demo.accessiblenav.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, Long> {

    Optional<ApiKeyEntity> findByKeyIdAndActiveTrue(String keyId);

    List<ApiKeyEntity> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<ApiKeyEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
