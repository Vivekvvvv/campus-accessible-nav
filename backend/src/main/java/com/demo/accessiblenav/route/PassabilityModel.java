package com.demo.accessiblenav.route;

import com.demo.accessiblenav.obstacle.ObstacleReportEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Bayesian passability probability model.
 * Computes a 0.0~1.0 probability that an edge is passable,
 * based on recent obstacle reports with time-decay weighting.
 */
@Component
public class PassabilityModel {

    private static final double BASE_PROBABILITY = 1.0;
    private static final double REPORT_WEIGHT = 0.15;
    private static final Duration DECAY_HALF_LIFE = Duration.ofHours(24);

    /**
     * Compute passability probability for an edge given its recent reports.
     * @param reports recent obstacle reports (PENDING or APPROVED) for this edge
     * @param now     current time
     * @return probability between 0.01 and 1.0
     */
    public double compute(List<ObstacleReportEntity> reports, Instant now) {
        if (reports == null || reports.isEmpty()) {
            return BASE_PROBABILITY;
        }

        double penalty = 0.0;
        for (ObstacleReportEntity report : reports) {
            double age = Duration.between(report.getCreatedAt(), now).toMillis();
            double halfLifeMs = DECAY_HALF_LIFE.toMillis();
            double decay = Math.exp(-0.693 * age / halfLifeMs); // ln(2) ≈ 0.693
            double weight = REPORT_WEIGHT * decay;

            // Approved reports carry more weight
            if ("APPROVED".equals(report.getStatus())) {
                weight *= 1.5;
            }

            // Scale by confirmation count
            weight *= Math.min(report.getConfirmCount(), 5);

            penalty += weight;
        }

        double probability = BASE_PROBABILITY - penalty;
        return Math.max(0.01, Math.min(1.0, probability));
    }
}
