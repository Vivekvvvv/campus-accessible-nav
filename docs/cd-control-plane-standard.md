# CD Control Plane Standard

This document standardizes canary rollout control-plane secrets and command contracts.

## 1. Required secrets

- `PROMETHEUS_BASE_URL`
- `CANARY_SET_WEIGHT_CMD` (must reference `$WEIGHT`)
- `CANARY_PROMOTE_CMD`
- `CANARY_ROLLBACK_CMD`

## 2. Optional mitigation secrets

- `RATE_LIMIT_TIGHTEN_CMD`
- `RATE_LIMIT_RELAX_CMD`

If optional commands are missing, rollout still works, but only degrade/rollback actions apply.

## 3. Command contract

`CANARY_SET_WEIGHT_CMD`:
- receives target weight via environment variable `$WEIGHT`
- should be idempotent
- should fail fast on invalid weight

`CANARY_PROMOTE_CMD` / `CANARY_ROLLBACK_CMD`:
- should be idempotent
- should emit operation log lines with release id / environment

`RATE_LIMIT_TIGHTEN_CMD` / `RATE_LIMIT_RELAX_CMD`:
- should target backend ingress/API gateway policy
- should not require application restart

## 4. Validation

CI/CD runs:
- `scripts/cd/validate-control-plane-env.sh`

Deployment refuses dry-run in production:
- `CANARY_REQUIRE_ACTIONS=true`

## 5. Reference

- `.github/workflows/cd.yml`
- `scripts/cd/run-canary-rollout.sh`
- `scripts/cd/handle-alert-actions.sh`
