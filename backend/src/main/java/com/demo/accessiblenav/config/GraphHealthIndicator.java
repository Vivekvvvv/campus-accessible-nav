package com.demo.accessiblenav.config;

import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.graph.EdgeRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 路网数据健康检查
 */
@Component
public class GraphHealthIndicator implements HealthIndicator {

    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;

    public GraphHealthIndicator(NodeRepository nodeRepository, EdgeRepository edgeRepository) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
    }

    @Override
    public Health health() {
        try {
            long nodeCount = nodeRepository.count();
            long edgeCount = edgeRepository.count();

            if (nodeCount == 0) {
                return Health.down()
                        .withDetail("reason", "路网数据为空")
                        .withDetail("nodeCount", nodeCount)
                        .withDetail("edgeCount", edgeCount)
                        .build();
            }

            if (nodeCount < 10) {
                return Health.status("DEGRADED")
                        .withDetail("warning", "路网数据不足")
                        .withDetail("nodeCount", nodeCount)
                        .withDetail("edgeCount", edgeCount)
                        .build();
            }

            return Health.up()
                    .withDetail("nodeCount", nodeCount)
                    .withDetail("edgeCount", edgeCount)
                    .withDetail("avgEdgesPerNode", edgeCount > 0 ? String.format("%.2f", (double) edgeCount / nodeCount) : "0")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
