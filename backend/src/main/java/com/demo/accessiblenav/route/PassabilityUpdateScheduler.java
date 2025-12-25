package com.demo.accessiblenav.route;

import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.obstacle.ObstacleReportEntity;
import com.demo.accessiblenav.obstacle.ObstacleReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Scheduled job that recalculates passability probabilities for all edges
 * based on recent obstacle reports.
 */
@Component
public class PassabilityUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(PassabilityUpdateScheduler.class);

    private final EdgeRepository edgeRepository;
    private final ObstacleReportRepository reportRepository;
    private final PassabilityModel model;

    public PassabilityUpdateScheduler(EdgeRepository edgeRepository,
                                      ObstacleReportRepository reportRepository,
                                      PassabilityModel model) {
        this.edgeRepository = edgeRepository;
        this.reportRepository = reportRepository;
        this.model = model;
    }

    @Scheduled(fixedRate = 3600_000) // every hour
    @Transactional
    public void updatePassabilities() {
        Instant now = Instant.now();
        Instant since = now.minus(7, ChronoUnit.DAYS);

        // Fetch all reports from the last 7 days
        List<ObstacleReportEntity> allReports = reportRepository.findAll().stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(since))
                .filter(r -> "PENDING".equals(r.getStatus()) || "APPROVED".equals(r.getStatus()))
                .toList();

        // Group by edge ID
        Map<Long, List<ObstacleReportEntity>> byEdge = allReports.stream()
                .filter(r -> r.getEdge() != null)
                .collect(Collectors.groupingBy(r -> r.getEdge().getId()));

        int updated = 0;
        List<EdgeEntity> allEdges = edgeRepository.findAll();
        for (EdgeEntity edge : allEdges) {
            List<ObstacleReportEntity> edgeReports = byEdge.get(edge.getId());
            double newProb = model.compute(edgeReports, now);
            if (Math.abs(edge.getPassabilityProbability() - newProb) > 0.001) {
                edge.setPassabilityProbability(newProb);
                edgeRepository.save(edge);
                updated++;
            }
        }

        if (updated > 0) {
            log.info("Updated passability for {} edges", updated);
        }
    }
}
