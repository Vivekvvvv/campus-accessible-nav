package com.demo.accessiblenav.obstacle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ObstacleReportRepository extends JpaRepository<ObstacleReportEntity, Long> {

	List<ObstacleReportEntity> findByStatusOrderByCreatedAtDesc(String status);

	List<ObstacleReportEntity> findByStatusAndTenantIdOrderByCreatedAtDesc(String status, String tenantId);

	long countByStatus(String status);

	List<ObstacleReportEntity> findBySubmitterIdOrderByCreatedAtDesc(String submitterId);

	List<ObstacleReportEntity> findBySubmitterIdAndStatusOrderByCreatedAtDesc(String submitterId, String status);

	List<ObstacleReportEntity> findBySubmitterIdAndTenantIdOrderByCreatedAtDesc(String submitterId, String tenantId);

	List<ObstacleReportEntity> findBySubmitterIdAndStatusAndTenantIdOrderByCreatedAtDesc(String submitterId, String status, String tenantId);

	void deleteAllByEdge_IdIn(List<Long> edgeIds);

	@Query("SELECT r FROM ObstacleReportEntity r WHERE r.dedupeKey = :key AND r.status IN ('PENDING', 'APPROVED') AND r.createdAt > :since ORDER BY r.createdAt DESC")
	List<ObstacleReportEntity> findRecentByDedupeKey(@Param("key") String key, @Param("since") Instant since);

	@Query("SELECT r FROM ObstacleReportEntity r WHERE r.dedupeKey = :key AND r.tenantId = :tenantId AND r.status IN ('PENDING', 'APPROVED') AND r.createdAt > :since ORDER BY r.createdAt DESC")
	List<ObstacleReportEntity> findRecentByDedupeKeyAndTenant(@Param("key") String key,
	                                                          @Param("since") Instant since,
	                                                          @Param("tenantId") String tenantId);

	@Query("SELECT r FROM ObstacleReportEntity r WHERE r.status = 'PENDING' AND r.escalated = false AND r.createdAt < :threshold ORDER BY r.createdAt ASC")
	List<ObstacleReportEntity> findPendingCreatedBefore(@Param("threshold") Instant threshold);

	@Query("SELECT r FROM ObstacleReportEntity r WHERE r.status = 'PENDING' AND r.escalated = false AND r.tenantId = :tenantId AND r.createdAt < :threshold ORDER BY r.createdAt ASC")
	List<ObstacleReportEntity> findPendingCreatedBeforeAndTenant(@Param("threshold") Instant threshold, @Param("tenantId") String tenantId);
}
