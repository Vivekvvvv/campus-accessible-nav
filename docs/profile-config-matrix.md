# Profile/Config Matrix

This document is the single source of truth for runtime profile behavior across local, CI, and production.

| profile | primary usage | DB | auth | actuator | rate limit | OpenAPI |
|---|---|---|---|---|---|---|
| `dev` | local backend + frontend | PostgreSQL + PostGIS | JWT | `health,info,prometheus,metrics,caches,env` | enabled, local | enabled |
| `h2` | demo/smoke without PostgreSQL | H2 | JWT | `health,info,prometheus` | disabled | inherited |
| `prod` | production | PostgreSQL + PostGIS | JWT | `health,info,prometheus` | enabled, Redis-backed | disabled |
| `test` | unit + integration (non-IT) | H2 | JWT | `health,info,prometheus` | disabled | inherited |
| `it` | `mvn -Pit verify` | PostgreSQL + PostGIS | JWT | `health,info,prometheus` | disabled | disabled |

Mapped files:
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-h2.yml`
- `backend/src/main/resources/application-prod.yml`
- `backend/src/main/resources/application-test.yml`
- `backend/src/main/resources/application-it.yml`

Mapped scripts/docs:
- `.env.example`
- `start.ps1`
- `README.md`
- `RUNBOOK.md`
