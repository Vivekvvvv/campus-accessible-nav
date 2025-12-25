package com.demo.accessiblenav.navigation.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Navigation session housekeeping (timeout / stale session cleanup).
 *
 * Note: Spring's @Scheduled fixedDelay expects milliseconds, so we keep fixedDelayMs as a long.
 */
@Component
@ConfigurationProperties(prefix = "app.navigation.housekeeping")
public class NavigationHousekeepingProperties {

    /**
     * Global switch to disable housekeeping (useful in some test envs).
     */
    private boolean enabled = true;

    /**
     * End sessions whose last location update is older than this threshold.
     */
    private Duration staleLocation = Duration.ofMinutes(10);

    /**
     * End sessions whose startedAt is older than this threshold.
     */
    private Duration maxDuration = Duration.ofHours(4);

    /**
     * Sweep interval in milliseconds.
     */
    private long fixedDelayMs = 60_000L;

    /**
     * Max number of sessions to process per sweep, per category (timeout vs stale).
     */
    private int batchSize = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getStaleLocation() {
        return staleLocation;
    }

    public void setStaleLocation(Duration staleLocation) {
        this.staleLocation = staleLocation;
    }

    public Duration getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(Duration maxDuration) {
        this.maxDuration = maxDuration;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}

