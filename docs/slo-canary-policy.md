# SLO and Canary Rollback Policy

## 1. SLO definitions

Core SLO signals (5-minute window):

- route availability:
  - query: `sum(rate(route_requests_total{result="success"}[5m])) / clamp_min(sum(rate(route_requests_total[5m])), 1e-9)`
  - objective: `>= 0.98`
- HTTP 5xx ratio:
  - query: `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count[5m])), 1e-9)`
  - objective: `<= 0.02`
- route p95 latency:
  - query: `histogram_quantile(0.95, sum(rate(route_calculation_duration_seconds_bucket[5m])) by (le))`
  - objective: `<= 1.5s`

## 2. Alert binding

These alerts map directly to SLO breaches:

- `SLOAvailabilityFastBurn`
- `SLOHttp5xxFastBurn`
- `SLORouteP95FastBurn`

The same thresholds are used by canary control-plane gating.

## 3. Canary rollout

CD uses `scripts/cd/run-canary-rollout.sh`:

- rollout steps: `5 -> 25 -> 50 -> 100`
- per-step wait: `120s` (configurable)
- at each step, evaluate the 3 SLO queries above
- if threshold breaches:
  - tighten rate limit (`RATE_LIMIT_TIGHTEN_CMD`, optional),
  - degrade canary weight (`SLO_DEGRADE_WEIGHT`, default 10),
  - wait and recheck (`SLO_RECHECK_SECONDS`, default 60),
  - rollback (`CANARY_ROLLBACK_CMD`) if still failing.

Required env/secret commands:

- `CANARY_SET_WEIGHT_CMD` (uses `$WEIGHT`)
- `CANARY_PROMOTE_CMD`
- `CANARY_ROLLBACK_CMD`
- `PROMETHEUS_BASE_URL`
- `RATE_LIMIT_TIGHTEN_CMD` (optional)
- `RATE_LIMIT_RELAX_CMD` (optional)

## 4. Alert-to-action binding

Alertmanager receiver `control-plane-actions` forwards critical SLO/navigation alerts to a webhook.

Recommended action script:
- `scripts/cd/handle-alert-actions.sh`

Default mapping:
- `SLOAvailabilityFastBurn|SLOHttp5xxFastBurn|SLORouteP95FastBurn` -> degrade + tighten + rollback
- `NavigationObstacleRerouteSpike|NavigationHazardQueriesHighPerSession|NavigationOffRouteWarnRatioHigh` -> degrade + tighten
