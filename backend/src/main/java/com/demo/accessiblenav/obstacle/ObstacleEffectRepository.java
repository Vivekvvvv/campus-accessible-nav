package com.demo.accessiblenav.obstacle;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ObstacleEffectRepository extends JpaRepository<ObstacleEffectEntity, Long> {

    Optional<ObstacleEffectEntity> findByEdge_IdAndActiveTrue(Long edgeId);

    List<ObstacleEffectEntity> findAllByReport_IdAndActiveTrue(Long reportId);

    @Query("select e from ObstacleEffectEntity e join fetch e.report r where e.active = true and e.endAt is not null and e.endAt <= ?1")
    List<ObstacleEffectEntity> findActiveExpiredEffects(Instant now);

    @Query("select min(e.endAt) from ObstacleEffectEntity e where e.active = true and e.disabled = true and e.endAt is not null and e.endAt > ?1")
    Instant findNextExpiryAfter(Instant now);

    @Query("select min(e.endAt) from ObstacleEffectEntity e where e.active = true and e.disabled = true and e.tenantId = ?2 and e.endAt is not null and e.endAt > ?1")
    Instant findNextExpiryAfterByTenant(Instant now, String tenantId);

    @Query("select count(e) from ObstacleEffectEntity e where e.active = true and e.disabled = true and (e.endAt is null or e.endAt > ?1)")
    long countActiveDisabledNotExpired(Instant now);

    @Query("select e.edge.id from ObstacleEffectEntity e where e.active = true and e.disabled = true and (e.endAt is null or e.endAt > ?1)")
    List<Long> findActiveDisabledEdgeIds(Instant now);

    @Query("select e.edge.id from ObstacleEffectEntity e where e.active = true and e.disabled = true and e.tenantId = ?2 and (e.endAt is null or e.endAt > ?1)")
    List<Long> findActiveDisabledEdgeIdsByTenant(Instant now, String tenantId);

    @Query("select e from ObstacleEffectEntity e join fetch e.edge where e.active = true and e.disabled = true and (e.endAt is null or e.endAt > ?1)")
    List<ObstacleEffectEntity> findActiveDisabledEffects(Instant now);

    @Query("""
            select ef from ObstacleEffectEntity ef
              join fetch ef.edge ed
              join fetch ed.fromNode fn
              join fetch ed.toNode tn
             where ef.active = true
               and ef.disabled = true
               and (ef.endAt is null or ef.endAt > :now)
               and (
                 (fn.lat between :minLat and :maxLat and fn.lng between :minLng and :maxLng)
                 or
                 (tn.lat between :minLat and :maxLat and tn.lng between :minLng and :maxLng)
               )
            """)
    List<ObstacleEffectEntity> findActiveDisabledEffectsInBBox(Instant now,
                                                              BigDecimal minLat,
                                                              BigDecimal maxLat,
                                                              BigDecimal minLng,
                                                              BigDecimal maxLng,
                                                              Pageable pageable);

    @Query("""
            select ef from ObstacleEffectEntity ef
              join fetch ef.edge ed
              join fetch ed.fromNode fn
              join fetch ed.toNode tn
             where ef.active = true
               and ef.disabled = true
               and ef.tenantId = :tenantId
               and (ef.endAt is null or ef.endAt > :now)
               and (
                 (fn.lat between :minLat and :maxLat and fn.lng between :minLng and :maxLng)
                 or
                 (tn.lat between :minLat and :maxLat and tn.lng between :minLng and :maxLng)
               )
            """)
    List<ObstacleEffectEntity> findActiveDisabledEffectsInBBoxByTenant(Instant now,
                                                                      String tenantId,
                                                                      BigDecimal minLat,
                                                                      BigDecimal maxLat,
                                                                      BigDecimal minLng,
                                                                      BigDecimal maxLng,
                                                                      Pageable pageable);

    void deleteAllByEdge_IdIn(List<Long> edgeIds);
}
