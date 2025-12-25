package com.demo.accessiblenav.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmergencyZoneRepository extends JpaRepository<EmergencyZoneEntity, Long> {

    List<EmergencyZoneEntity> findByActiveTrueOrderByNameAsc();

    List<EmergencyZoneEntity> findByActiveTrueAndTenantIdOrderByNameAsc(String tenantId);

    @Query("SELECT z FROM EmergencyZoneEntity z WHERE z.active = true " +
           "AND z.tenantId = :tenantId " +
           "AND z.centerLat BETWEEN :minLat AND :maxLat " +
           "AND z.centerLng BETWEEN :minLng AND :maxLng")
    List<EmergencyZoneEntity> findZonesNear(@Param("tenantId") String tenantId,
                                            @Param("minLat") double minLat,
                                            @Param("maxLat") double maxLat,
                                            @Param("minLng") double minLng,
                                            @Param("maxLng") double maxLng);
}
