#!/usr/bin/env bash
set -euo pipefail

# Alertmanager webhook action router.
# Usage:
#   ./scripts/cd/handle-alert-actions.sh <alertmanager-payload.json>
#
# Env:
# - CANARY_SET_WEIGHT_CMD
# - CANARY_ROLLBACK_CMD
# - RATE_LIMIT_TIGHTEN_CMD (optional)
# - RATE_LIMIT_RELAX_CMD (optional)
# - CRITICAL_DEGRADE_WEIGHT (default 10)
# - WARNING_DEGRADE_WEIGHT (default 25)
# - ALERT_ACTION_DRY_RUN (default false)

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[alert-action] missing command: $1"
    exit 1
  }
}

require_cmd jq

PAYLOAD_FILE="${1:-${ALERT_PAYLOAD_FILE:-}}"
if [[ -z "$PAYLOAD_FILE" ]]; then
  echo "Usage: $0 <alertmanager-payload.json>"
  exit 1
fi
if [[ ! -f "$PAYLOAD_FILE" ]]; then
  echo "[alert-action] payload not found: $PAYLOAD_FILE"
  exit 1
fi

CRITICAL_DEGRADE_WEIGHT="${CRITICAL_DEGRADE_WEIGHT:-10}"
WARNING_DEGRADE_WEIGHT="${WARNING_DEGRADE_WEIGHT:-25}"
ALERT_ACTION_DRY_RUN="${ALERT_ACTION_DRY_RUN:-false}"

run_cmd() {
  local label="$1"
  local cmd="${2:-}"
  if [[ -z "$cmd" ]]; then
    echo "[alert-action] skip '$label' (cmd not configured)."
    return 0
  fi
  if [[ "$ALERT_ACTION_DRY_RUN" == "true" ]]; then
    echo "[alert-action][dry-run] $label: $cmd"
    return 0
  fi
  echo "[alert-action] $label"
  eval "$cmd"
}

degrade_weight() {
  local target="$1"
  if [[ -z "${CANARY_SET_WEIGHT_CMD:-}" ]]; then
    echo "[alert-action] skip degrade to ${target}% (CANARY_SET_WEIGHT_CMD missing)."
    return 0
  fi
  export WEIGHT="$target"
  run_cmd "set canary weight ${target}%" "$CANARY_SET_WEIGHT_CMD"
}

handle_firing_alert() {
  local alertname="$1"
  local severity="$2"

  case "$alertname" in
    SLOAvailabilityFastBurn|SLOHttp5xxFastBurn|SLORouteP95FastBurn)
      echo "[alert-action] critical SLO alert: ${alertname} (${severity})"
      degrade_weight "$CRITICAL_DEGRADE_WEIGHT"
      run_cmd "tighten rate limit (critical SLO)" "${RATE_LIMIT_TIGHTEN_CMD:-}"
      run_cmd "rollback canary (critical SLO)" "${CANARY_ROLLBACK_CMD:-}"
      ;;
    NavigationObstacleRerouteSpike|NavigationHazardQueriesHighPerSession|NavigationOffRouteWarnRatioHigh)
      echo "[alert-action] navigation warning alert: ${alertname} (${severity})"
      degrade_weight "$WARNING_DEGRADE_WEIGHT"
      run_cmd "tighten rate limit (navigation warning)" "${RATE_LIMIT_TIGHTEN_CMD:-}"
      ;;
    *)
      echo "[alert-action] no action mapping for alert: ${alertname} (${severity})"
      ;;
  esac
}

firing_count="$(jq '[.alerts[] | select(.status=="firing")] | length' "$PAYLOAD_FILE")"
echo "[alert-action] firing alerts: ${firing_count}"

if [[ "$firing_count" -eq 0 ]]; then
  # If no active alert remains, try to relax rate limit.
  run_cmd "relax rate limit (all alerts resolved)" "${RATE_LIMIT_RELAX_CMD:-}"
  exit 0
fi

while IFS=$'\t' read -r alertname severity; do
  handle_firing_alert "$alertname" "$severity"
done < <(jq -r '.alerts[] | select(.status=="firing") | [.labels.alertname, (.labels.severity // "unknown")] | @tsv' "$PAYLOAD_FILE")

echo "[alert-action] done."
