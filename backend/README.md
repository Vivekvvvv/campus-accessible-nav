# accessible-nav-backend

## Requirements
- JDK 21+
- Maven 3.9+
- PostgreSQL 15+
- PostGIS extension

Why PostGIS is required:
- Flyway migration `V1__init.sql` runs `CREATE EXTENSION IF NOT EXISTS postgis;`

## Database Setup

Use your own PostgreSQL/PostGIS instance:

```sql
CREATE DATABASE accessible_nav;
\c accessible_nav
CREATE EXTENSION IF NOT EXISTS postgis;
```

## Configuration

Backend reads env vars (defaults shown):
- `DB_URL` default: `jdbc:postgresql://localhost:5432/accessible_nav?currentSchema=public`
- `DB_USERNAME` default: `postgres`
- `DB_PASSWORD` default: `postgres`
- `DB_SCHEMA` default: `public`
- `CORS_ALLOWED_ORIGINS` default: `http://localhost:5173`

## Run

```bash
mvn -s .mvn/settings.xml spring-boot:run
```

## Integration Tests

`mvn -Pit verify` requires an external PostgreSQL/PostGIS database. Set:
- `IT_DB_URL`
- `IT_DB_USERNAME` (optional, default `postgres`)
- `IT_DB_PASSWORD` (optional, default `postgres`)
- `IT_DB_SCHEMA` (optional, default `it`)

## Observability
- Swagger UI: `/swagger-ui/index.html`
- OpenAPI: `/v3/api-docs`
- Health: `/actuator/health`
- Prometheus: `/actuator/prometheus`

## Migrations

Flyway migrations live in:
- `src/main/resources/db/migration/`
