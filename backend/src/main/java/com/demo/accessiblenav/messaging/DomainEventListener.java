package com.demo.accessiblenav.messaging;

import com.demo.accessiblenav.messaging.events.EmergencyTriggeredEvent;
import com.demo.accessiblenav.messaging.events.GraphUpdatedEvent;
import com.demo.accessiblenav.messaging.events.ObstacleReportedEvent;
import com.demo.accessiblenav.route.RouteCache;
import com.demo.accessiblenav.spatial.SpatialIndexManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 领域事件监听器
 * 响应各种领域事件，执行相应的业务逻辑
 */
@Component
public class DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final SpatialIndexManager spatialIndexManager;
    private final RouteCache routeCache;

    public DomainEventListener(SpatialIndexManager spatialIndexManager, RouteCache routeCache) {
        this.spatialIndexManager = spatialIndexManager;
        this.routeCache = routeCache;
    }

    /**
     * 监听路网更新事件
     * 重建空间索引并清除路由缓存
     */
    @EventListener
    @Async
    public void onGraphUpdated(GraphUpdatedEvent event) {
        log.info("收到路网更新事件: type={}, aggregateId={}",
                event.getUpdateType(), event.getAggregateId());

        switch (event.getUpdateType()) {
            case NODE_ADDED:
            case NODE_REMOVED:
            case NODE_UPDATED:
                // 增量更新节点索引
                spatialIndexManager.rebuildNodeIndex();
                break;

            case EDGE_ADDED:
            case EDGE_REMOVED:
            case EDGE_UPDATED:
                // 增量更新边索引
                spatialIndexManager.rebuildEdgeIndex();
                break;

            case FULL_REBUILD:
                // 完整重建所有索引
                spatialIndexManager.rebuildAllIndexes();
                break;
        }

        // 路网变化时清除路由缓存 (L1 + L2)
        routeCache.clear();
        log.info("路网更新后已清除路由缓存");
    }

    /**
     * 监听障碍上报事件
     */
    @EventListener
    @Async
    public void onObstacleReported(ObstacleReportedEvent event) {
        log.info("收到障碍上报事件: aggregateId={}, reporter={}, requiresReview={}",
                event.getAggregateId(), event.getReporterId(), event.isRequiresReview());

        // 障碍物影响路由，清除缓存
        routeCache.clear();
        log.info("障碍上报后已清除路由缓存");

        if (event.isRequiresReview()) {
            log.info("障碍上报需要人工审核: {}", event.getAggregateId());
            // 发送审核通知
        }
    }

    /**
     * 监听紧急求助事件
     */
    @EventListener
    public void onEmergencyTriggered(EmergencyTriggeredEvent event) {
        // 紧急事件同步处理，确保及时响应
        log.warn("收到紧急求助事件: userId={}, type={}, location=({}, {})",
                event.getUserId(), event.getEmergencyType(),
                event.getLat(), event.getLng());

        // 这里可以添加：
        // - 发送短信通知安保
        // - 推送到监控大屏
        // - 记录到应急日志
    }
}
