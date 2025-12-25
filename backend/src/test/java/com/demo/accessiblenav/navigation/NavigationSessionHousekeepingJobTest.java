package com.demo.accessiblenav.navigation;

import com.demo.accessiblenav.navigation.persistence.NavigationSessionEntity;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionEventRepository;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionRepository;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionStatus;
import com.demo.accessiblenav.navigation.service.NavigationHousekeepingProperties;
import com.demo.accessiblenav.navigation.service.NavigationSessionHousekeepingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NavigationSessionHousekeepingJobTest {

    @Autowired
    NavigationSessionRepository sessionRepository;

    @Autowired
    NavigationSessionEventRepository eventRepository;

    @Autowired
    NavigationSessionHousekeepingJob job;

    @Autowired
    NavigationHousekeepingProperties props;

    @BeforeEach
    void reset() {
        eventRepository.deleteAll();
        sessionRepository.deleteAll();

        // Make the sweep deterministic and fast for tests.
        props.setEnabled(true);
        props.setBatchSize(100);
        props.setMaxDuration(Duration.ofMinutes(30));
        props.setStaleLocation(Duration.ofMinutes(10));
    }

    @Test
    void sweepOnce_shouldEndSessionsByMaxDuration() {
        Instant now = Instant.now();
        NavigationSessionEntity s = new NavigationSessionEntity();
        s.setId(UUID.randomUUID().toString());
        s.setUserId("u1");
        s.setStatus(NavigationSessionStatus.ACTIVE);
        s.setMode("WALK");
        s.setStartLat(23.0);
        s.setStartLng(113.0);
        s.setDestinationLat(23.1);
        s.setDestinationLng(113.1);
        s.setDeviationCount(0);
        s.setRerouteCount(0);
        s.setCreatedAt(now.minus(Duration.ofHours(2)));
        s.setStartedAt(now.minus(Duration.ofHours(2)));
        s.setLastLocationAt(now.minus(Duration.ofMinutes(1))); // fresh
        sessionRepository.save(s);

        job.sweepOnce();

        NavigationSessionEntity updated = sessionRepository.findById(s.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(NavigationSessionStatus.ENDED);
        assertThat(updated.getEndedAt()).isNotNull();

        assertThat(eventRepository.findAll())
                .anySatisfy(ev -> assertThat(ev.getPayloadJson()).contains("TIMEOUT"));
    }

    @Test
    void sweepOnce_shouldEndSessionsByStaleLocation() {
        Instant now = Instant.now();
        NavigationSessionEntity s = new NavigationSessionEntity();
        s.setId(UUID.randomUUID().toString());
        s.setUserId("u1");
        s.setStatus(NavigationSessionStatus.ACTIVE);
        s.setMode("WALK");
        s.setStartLat(23.0);
        s.setStartLng(113.0);
        s.setDestinationLat(23.1);
        s.setDestinationLng(113.1);
        s.setDeviationCount(0);
        s.setRerouteCount(0);
        s.setCreatedAt(now.minus(Duration.ofMinutes(20)));
        s.setStartedAt(now.minus(Duration.ofMinutes(20)));
        s.setLastLocationAt(now.minus(Duration.ofMinutes(20))); // stale
        sessionRepository.save(s);

        job.sweepOnce();

        NavigationSessionEntity updated = sessionRepository.findById(s.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(NavigationSessionStatus.ENDED);
        assertThat(updated.getEndedAt()).isNotNull();

        assertThat(eventRepository.findAll())
                .anySatisfy(ev -> assertThat(ev.getPayloadJson()).contains("STALE_LOCATION"));
    }
}

