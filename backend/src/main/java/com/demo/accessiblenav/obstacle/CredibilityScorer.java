package com.demo.accessiblenav.obstacle;

import com.demo.accessiblenav.auth.UserAccount;
import com.demo.accessiblenav.auth.UserAccountService;
import org.springframework.stereotype.Component;

/**
 * Computes a confidence score (0.0 ~ 1.0) for an obstacle report based on
 * submitter credit, photo evidence, and confirmation count.
 */
@Component
public class CredibilityScorer {

    private final UserAccountService userAccountService;

    public CredibilityScorer(UserAccountService userAccountService) {
        this.userAccountService = userAccountService;
    }

    /**
     * Compute confidence score for the given report.
     *
     * Factors:
     *   base           = 0.50
     *   user credit    = 0 ~ 0.20 (based on credit score, max at 100)
     *   photo evidence = +0.15 if photos attached
     *   confirm count  = +0.05 per confirmation, max 0.15
     */
    public double computeConfidence(ObstacleReportEntity report) {
        double score = 0.50;

        // User credit factor
        if (report.getSubmitterId() != null && !report.getSubmitterId().trim().isEmpty()) {
            UserAccount user = userAccountService.findByUsername(report.getSubmitterId().trim());
            if (user != null) {
                int credit = user.getCreditScore();
                double creditFactor = Math.min(1.0, Math.max(0.0, credit / 100.0));
                score += 0.20 * creditFactor;
            }
        }

        // Photo evidence factor
        if (report.getPhotoUrls() != null && !report.getPhotoUrls().trim().isEmpty()) {
            score += 0.15;
        }

        // Confirm count factor
        int confirms = report.getConfirmCount();
        if (confirms > 1) {
            score += Math.min(0.15, (confirms - 1) * 0.05);
        }

        return Math.min(1.0, Math.max(0.0, score));
    }
}
