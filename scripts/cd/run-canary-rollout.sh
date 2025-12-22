#!/usr/bin/env bash
set -euo pipefail

# Canary rollout with automatic rollback based on Prometheus SLO queries.
#
# Required env when not running dry-run:
# - PROMETHEUS_BASE_URL
# - CANARY_SET_WEIGHT_CMD (shell snippet, uses $WEIGHT)
# - CANARY_PROMOTE_CMD
# - CANARY_ROLLBACK_CMD
#
# Optional thresholds:
# - SLO_ROUTE_AVAIL_MIN (default 0.98)
# - SLO_HTTP_5XX_MAX (default 0.02)
# - SLO_ROUTE_P95_MAX_SEC (default 1.5)
# - SLO_DEGRADE_WEIGHT (default 10)
# - SLO_RECHECK_SECONDS (default 60)
# - CANARY_STEPS (default "5 25 50 100")
# - CANARY_PAUSE_SECONDS (default 120)
# - RATE_LIMIT_TIGHTEN_CMD (optional)
# - RATE_LIMIT_RELAX_CMD (optional)

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing command: $1"
    exit 1
  }
}

require_cmd curl
require_cmd jq

SLO_ROUTE_AVAIL_MIN="${SLO_ROUTE_AVAIL_MIN:-0.98}"
SLO_HTTP_5XX_MAX="${SLO_HTTP_5XX_MAX:-0.02}"
SLO_ROUTE_P95_MAX_SEC="${SLO_ROUTE_P95_MAX_SEC:-1.5}"
SLO_DEGRADE_WEIGHT="${SLO_DEGRADE_WEIGHT:-10}"
SLO_RECHECK_SECONDS="${SLO_RECHECK_SECONDS:-60}"
CANARY_STEPS="${CANARY_STEPS:-5 25 50 100}"
CANARY_PAUSE_SECONDS="${CANARY_PAUSE_SECONDS:-120}"

LAST_SLO_BREACH=""
RATE_LIMIT_TIGHTENED=false

DRY_RUN=false
CANARY_REQUIRE_ACTIONS="${CANARY_REQUIRE_ACTIONS:-false}"
if [[ -z "${PROMETHEUS_BASE_URL:-}" || -z "${CANARY_SET_WEIGHT_CMD:-}" || -z "${CANARY_PROMOTE_CMD:-}" || -z "${CANARY_ROLLBACK_CMD:-}" ]]; then
  if [[ "${CANARY_REQUIRE_ACTIONS}" == "true" ]]; then
    echo "[canary] missing required env while CANARY_REQUIRE_ACTIONS=true; refusing to run dry-run."
    exit 1
  fi
  DRY_RUN=true
  echo "[canary] required env incomplete -> running in dry-run mode."
fi

run_cmd() {
  local label="$1"
  local cmd="$2"
  if [[ "$DRY_RUN" == "true" ]]; then
    echo "[canary][dry-run] $label: $cmd"
    return 0
  fi
  echo "[canary] $label"
  eval "$cmd"
}

run_optional_cmd() {
  local label="$1"
  local cmd="${2:-}"
  if [[ -z "$cmd" ]]; then
    echo "[canary] skip optional action '$label' (cmd not configured)."
    return 0
  fi
  run_cmd "$label" "$cmd"
}

query_prom() {
  local q="$1"
  curl -fsS --get \
    "${PROMETHEUS_BASE_URL%/}/api/v1/query" \
    --data-urlencode "query=$q" \
    | jq -r '.data.result[0].value[1] // "NaN"'
}

to_num() {
  local v="$1"
  if [[ "$v" == "NaN" || -z "$v" ]]; then
    echo "nan"
    return
  fi
  awk -v x="$v" 'BEGIN { printf "%.6f", x+0 }'
}

check_slo_gate() {
  if [[ "$DRY_RUN" == "true" ]]; then
    echo "[canary][dry-run] SLO gate skipped."
    LAST_SLO_BREACH=""
    return 0
  fi

  local q_route_avail='sum(rate(route_requests_total{result="success"}[5m])) / clamp_min(sum(rate(route_requests_total[5m])), 1e-9)'
  local q_http5xx='sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count[5m])), 1e-9)'
  local q_route_p95='histogram_quantile(0.95, sum(rate(route_calculation_duration_seconds_bucket[5m])) by (le))'

  local route_avail
  local http_5xx
  local route_p95
  route_avail="$(to_num "$(query_prom "$q_route_avail")")"
  http_5xx="$(to_num "$(query_prom "$q_http5xx")")"
  route_p95="$(to_num "$(query_prom "$q_route_p95")")"

  echo "[canary] SLO snapshot: route_avail=${route_avail}, http_5xx=${http_5xx}, route_p95=${route_p95}s"

  # If metrics are missing, fail safe and rollback.
  if [[ "$route_avail" == "nan" || "$http_5xx" == "nan" || "$route_p95" == "nan" ]]; then
    echo "[canary] Missing SLO metrics, aborting rollout."
    LAST_SLO_BREACH="MISSING_METRICS"
    return 1
  fi

  awk -v r="$route_avail" -v min="$SLO_ROUTE_AVAIL_MIN" 'BEGIN { exit (r+0 >= min+0) ? 0 : 1 }' || {
    echo "[canary] route availability below threshold: ${route_avail} < ${SLO_ROUTE_AVAIL_MIN}"
    LAST_SLO_BREACH="ROUTE_AVAILABILITY"
    return 1
  }
  awk -v e="$http_5xx" -v max="$SLO_HTTP_5XX_MAX" 'BEGIN { exit (e+0 <= max+0) ? 0 : 1 }' || {
    echo "[canary] http 5xx ratio above threshold: ${http_5xx} > ${SLO_HTTP_5XX_MAX}"
    LAST_SLO_BREACH="HTTP_5XX"
    return 1
  }
  awk -v p="$route_p95" -v max="$SLO_ROUTE_P95_MAX_SEC" 'BEGIN { exit (p+0 <= max+0) ? 0 : 1 }' || {
    echo "[canary] route p95 above threshold: ${route_p95}s > ${SLO_ROUTE_P95_MAX_SEC}s"
    LAST_SLO_BREACH="ROUTE_P95"
    return 1
  }

  LAST_SLO_BREACH=""
  return 0
}

rollback_now() {
  run_cmd "rollback" "$CANARY_ROLLBACK_CMD"
}

degrade_now() {
  local current_weight="$1"
  local target_weight="$SLO_DEGRADE_WEIGHT"
  if [[ -z "$target_weight" ]]; then
    echo "[canary] degrade weight not configured; skipping degrade action."
    return 0
  fi
  awk -v c="$current_weight" -v t="$target_weight" 'BEGIN { exit (c+0 > t+0) ? 0 : 1 }' || {
    echo "[canary] current weight (${current_weight}%) <= degrade target (${target_weight}%), skip degrade."
    return 0
  }
  export WEIGHT="$target_weight"
  run_cmd "degrade canary weight to ${target_weight}% (reason=${LAST_SLO_BREACH})" "$CANARY_SET_WEIGHT_CMD"
}

tighten_rate_limit_if_needed() {
  run_optional_cmd "tighten rate limit (reason=${LAST_SLO_BREACH})" "${RATE_LIMIT_TIGHTEN_CMD:-}"
  RATE_LIMIT_TIGHTENED=true
}

relax_rate_limit_if_needed() {
  if [[ "$RATE_LIMIT_TIGHTENED" != "true" ]]; then
    return 0
  fi
  run_optional_cmd "relax rate limit" "${RATE_LIMIT_RELAX_CMD:-}"
  RATE_LIMIT_TIGHTENED=false
}

echo "[canary] starting rollout steps: ${CANARY_STEPS}"
for WEIGHT in $CANARY_STEPS; do
  export WEIGHT
  run_cmd "set canary weight ${WEIGHT}%" "$CANARY_SET_WEIGHT_CMD"

  echo "[canary] waiting ${CANARY_PAUSE_SECONDS}s before SLO check..."
  sleep "$CANARY_PAUSE_SECONDS"

  if ! check_slo_gate; then
    echo "[canary] SLO gate failed at weight ${WEIGHT}% (reason=${LAST_SLO_BREACH}). Triggering auto actions..."
    tighten_rate_limit_if_needed
    degrade_now "$WEIGHT"
    echo "[canary] waiting ${SLO_RECHECK_SECONDS}s for post-action recheck..."
    sleep "$SLO_RECHECK_SECONDS"

    if ! check_slo_gate; then
      echo "[canary] post-action SLO recheck failed (reason=${LAST_SLO_BREACH}), rolling back."
      rollback_now
      exit 1
    fi

    echo "[canary] post-action SLO recheck passed, continue rollout."
  else
    relax_rate_limit_if_needed
  fi
done

run_cmd "promote canary to stable" "$CANARY_PROMOTE_CMD"
relax_rate_limit_if_needed
echo "[canary] rollout succeeded."
