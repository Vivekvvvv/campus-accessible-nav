package com.demo.accessiblenav.spatial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 空间索引管理服务
 * 负责索引的初始化、重建和维护
 */
@Service
public class SpatialIndexManager {

    private static final Logger log = LoggerFactory.getLogger(SpatialIndexManager.class);

    private final RTreeSpatialIndex spatialIndex;

    @PersistenceContext
    private EntityManager entityManager;

    public SpatialIndexManager(RTreeSpatialIndex spatialIndex) {
        this.spatialIndex = spatialIndex;
    }

    /**
     * 应用启动后自动初始化索引
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("应用启动完成，开始初始化空间索引...");
        rebuildAllIndexes();
    }

    /**
     * 重建所有索引
     */
    public void rebuildAllIndexes() {
        long startTime = System.currentTimeMillis();

        try {
            rebuildNodeIndex();
            rebuildEdgeIndex();
            rebuildFacilityIndex();

            long duration = System.currentTimeMillis() - startTime;
            RTreeSpatialIndex.SpatialIndexStats stats = spatialIndex.getStats();

            log.info("空间索引重建完成，耗时 {}ms - {}", duration, stats);
        } catch (Exception e) {
            log.error("空间索引重建失败", e);
        }
    }

    /**
     * 重建节点索引
     */
    @SuppressWarnings("unchecked")
    public void rebuildNodeIndex() {
        try {
            List<Object[]> results = entityManager.createNativeQuery(
                    "SELECT id, lat, lng FROM t_node WHERE lat IS NOT NULL AND lng IS NOT NULL"
            ).getResultList();

            List<RTreeSpatialIndex.SpatialNode> nodes = results.stream()
                    .map(row -> new RTreeSpatialIndex.SpatialNode(
                            ((Number) row[0]).longValue(),
                            ((Number) row[1]).doubleValue(),
                            ((Number) row[2]).doubleValue()
                    ))
                    .collect(Collectors.toList());

            spatialIndex.addNodes(nodes);
            log.info("节点索引重建完成: {} 个节点", nodes.size());
        } catch (Exception e) {
            log.warn("节点索引重建失败（表可能不存在）: {}", e.getMessage());
        }
    }

    /**
     * 重建边索引
     */
    @SuppressWarnings("unchecked")
    public void rebuildEdgeIndex() {
        try {
            List<Object[]> results = entityManager.createNativeQuery(
                    // Alias columns to avoid duplicated SQL aliases during Hibernate auto-discovery.
                    "SELECT e.id, n1.lat AS from_lat, n1.lng AS from_lng, n2.lat AS to_lat, n2.lng AS to_lng " +
                    "FROM t_edge e " +
                    "JOIN t_node n1 ON e.from_node_id = n1.id " +
                    "JOIN t_node n2 ON e.to_node_id = n2.id"
            ).getResultList();

            List<RTreeSpatialIndex.SpatialEdge> edges = results.stream()
                    .map(row -> new RTreeSpatialIndex.SpatialEdge(
                            ((Number) row[0]).longValue(),
                            ((Number) row[1]).doubleValue(),
                            ((Number) row[2]).doubleValue(),
                            ((Number) row[3]).doubleValue(),
                            ((Number) row[4]).doubleValue()
                    ))
                    .collect(Collectors.toList());

            spatialIndex.addEdges(edges);
            log.info("边索引重建完成: {} 条边", edges.size());
        } catch (Exception e) {
            log.warn("边索引重建失败（表可能不存在）: {}", e.getMessage());
        }
    }

    /**
     * 重建设施索引
     */
    @SuppressWarnings("unchecked")
    public void rebuildFacilityIndex() {
        try {
            List<Object[]> results = entityManager.createNativeQuery(
                    "SELECT id, lat, lng FROM t_facility WHERE lat IS NOT NULL AND lng IS NOT NULL"
            ).getResultList();

            List<RTreeSpatialIndex.SpatialNode> facilities = results.stream()
                    .map(row -> new RTreeSpatialIndex.SpatialNode(
                            ((Number) row[0]).longValue(),
                            ((Number) row[1]).doubleValue(),
                            ((Number) row[2]).doubleValue()
                    ))
                    .collect(Collectors.toList());

            spatialIndex.addFacilities(facilities);
            log.info("设施索引重建完成: {} 个设施", facilities.size());
        } catch (Exception e) {
            log.warn("设施索引重建失败（表可能不存在）: {}", e.getMessage());
        }
    }

    /**
     * 定期刷新索引（每小时）
     */
    @Scheduled(fixedRate = 3600000)
    public void scheduledRefresh() {
        log.debug("执行定时索引刷新...");
        rebuildAllIndexes();
    }

    /**
     * 获取索引统计信息
     */
    public RTreeSpatialIndex.SpatialIndexStats getStats() {
        return spatialIndex.getStats();
    }

    /**
     * 添加单个节点到索引
     */
    public void indexNode(Long nodeId, double lat, double lng) {
        spatialIndex.addNode(nodeId, lat, lng);
    }

    /**
     * 添加单条边到索引
     */
    public void indexEdge(Long edgeId, double fromLat, double fromLng, double toLat, double toLng) {
        spatialIndex.addEdge(edgeId, fromLat, fromLng, toLat, toLng);
    }

    /**
     * 添加单个设施到索引
     */
    public void indexFacility(Long facilityId, double lat, double lng) {
        spatialIndex.addFacility(facilityId, lat, lng);
    }
}
