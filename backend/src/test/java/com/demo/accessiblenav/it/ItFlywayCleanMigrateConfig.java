package com.demo.accessiblenav.it;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

/**
 * Integration tests should be deterministic.
 *
 * For profile "it", we always clean + migrate the dedicated schema to avoid
 * failures caused by leftover data (e.g., unique constraints on fixed coordinates).
 */
@TestConfiguration
@Profile("it")
public class ItFlywayCleanMigrateConfig {

    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy() {
        return (Flyway flyway) -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}

