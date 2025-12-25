package com.demo.accessiblenav.graph;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface NodeRepository extends JpaRepository<NodeEntity, Long> {
    Optional<NodeEntity> findByLatAndLng(BigDecimal lat, BigDecimal lng);

    List<NodeEntity> findAllByTenantId(String tenantId);

    Optional<NodeEntity> findByLatAndLngAndTenantId(BigDecimal lat, BigDecimal lng, String tenantId);

    @Query("select n from NodeEntity n where n.tenantId = :tenantId and n.lat between :minLat and :maxLat and n.lng between :minLng and :maxLng")
    List<NodeEntity> findNearbyByTenant(
            @Param("tenantId") String tenantId,
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            Pageable pageable
    );

    @Query("select n from NodeEntity n where n.lat between :minLat and :maxLat and n.lng between :minLng and :maxLng")
    List<NodeEntity> findNearby(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng,
            Pageable pageable
    );

    // KNN snapping (PostgreSQL/PostGIS only). Keep native SQL isolated here so callers can fall back on H2.
    @Query(
            value = "SELECT id FROM t_node " +
                    "WHERE geog IS NOT NULL " +
                    "  AND tenant_id = :tenantId " +
                    "  AND level = :level " +
                    "  AND ST_DWithin(geog, CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS public.geography), :radiusMeters) " +
                    "ORDER BY geog <-> CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS public.geography) " +
                    "LIMIT 1",
            nativeQuery = true
    )
    Long findNearestIdWithinMetersByTenant(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("tenantId") String tenantId,
            @Param("level") int level,
            @Param("radiusMeters") double radiusMeters
    );

    @Query(
            value = "SELECT id FROM t_node " +
                    "WHERE geog IS NOT NULL " +
                    "  AND tenant_id = :tenantId " +
                    "  AND level = :level " +
                    "  AND lat BETWEEN :minLat AND :maxLat " +
                    "  AND lng BETWEEN :minLng AND :maxLng " +
                    "  AND ST_DWithin(geog, CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS public.geography), :radiusMeters) " +
                    "ORDER BY geog <-> CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS public.geography) " +
                    "LIMIT 1",
            nativeQuery = true
    )
    Long findNearestIdWithinMetersAndBboxByTenant(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("tenantId") String tenantId,
            @Param("level") int level,
            @Param("radiusMeters") double radiusMeters,
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );
}
