package com.demo.accessiblenav.it;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real PostgreSQL/PostGIS integration test base.
 *
 * Requires an externally managed PostgreSQL/PostGIS database.
 * Set IT_DB_URL (and optional IT_DB_USERNAME/IT_DB_PASSWORD/IT_DB_SCHEMA)
 * before running: mvn -Pit verify
 *
 * The repository intentionally uses only externally managed PostgreSQL/PostGIS for this suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Transactional
public abstract class PostgresITBase {

    private static final Logger log = LoggerFactory.getLogger(PostgresITBase.class);

    private static final String ENV_DB_URL = "IT_DB_URL";
    private static final String ENV_DB_USERNAME = "IT_DB_USERNAME";
    private static final String ENV_DB_PASSWORD = "IT_DB_PASSWORD";
    private static final String ENV_DB_SCHEMA = "IT_DB_SCHEMA";

    private static boolean hasExternalDb() {
        String url = firstNonBlank(System.getProperty("it.db.url"), System.getenv(ENV_DB_URL));
        return url != null;
    }

    private static String firstNonBlank(String a, String b) {
        String aa = a == null ? null : a.trim();
        if (aa != null && !aa.isEmpty()) {
            return aa;
        }
        String bb = b == null ? null : b.trim();
        if (bb != null && !bb.isEmpty()) {
            return bb;
        }
        return null;
    }

    private static String schema() {
        // Dedicated schema prevents tests from touching user's existing tables.
        return firstNonBlank(System.getProperty("it.db.schema"), System.getenv(ENV_DB_SCHEMA)) != null
                ? firstNonBlank(System.getProperty("it.db.schema"), System.getenv(ENV_DB_SCHEMA))
                : "it";
    }

    @DynamicPropertySource
    static void registerDbProperties(DynamicPropertyRegistry registry) {
        if (!hasExternalDb()) {
            throw new IllegalStateException(
                    "Integration tests require an external PostgreSQL/PostGIS database.\n"
                            + "Set IT_DB_URL (and optionally IT_DB_USERNAME/IT_DB_PASSWORD/IT_DB_SCHEMA), then run: mvn -Pit verify"
            );
        }

        String url = firstNonBlank(System.getProperty("it.db.url"), System.getenv(ENV_DB_URL));
        String u = firstNonBlank(System.getProperty("it.db.username"), System.getenv(ENV_DB_USERNAME));
        String p = firstNonBlank(System.getProperty("it.db.password"), System.getenv(ENV_DB_PASSWORD));
        final String username = (u == null) ? "postgres" : u;
        final String password = (p == null) ? "postgres" : p;

        registry.add("spring.datasource.url", () -> withCurrentSchema(url, schema()));
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Force the real migration path for regression tests.
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.create-schemas", () -> true);
        registry.add("spring.flyway.schemas", PostgresITBase::schema);
        registry.add("spring.flyway.default-schema", PostgresITBase::schema);

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", PostgresITBase::schema);
        // Hibernate 6 (Spring Boot 3) renamed PostGIS dialect classes; PG10+ is a safe default for modern Postgres.
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.spatial.dialect.postgis.PostgisPG10Dialect");
    }

    private static String withCurrentSchema(String jdbcUrl, String schema) {
        if (jdbcUrl == null) {
            return null;
        }
        String url = jdbcUrl.trim();
        if (url.isEmpty()) {
            return url;
        }
        // If user already provided currentSchema, keep it as-is.
        if (url.toLowerCase().contains("currentschema=")) {
            return url;
        }
        // Include public so PostGIS types/functions (installed in public) are resolvable even when using a dedicated schema.
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
    }
}
