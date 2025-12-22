#!/usr/bin/env bash
set -euo pipefail

# Validate canary control-plane env/commands before production rollout.
#
# Required:
# - PROMETHEUS_BASE_URL
# - CANARY_SET_WEIGHT_CMD (must reference $WEIGHT)
# - CANARY_PROMOTE_CMD
# - CANARY_ROLLBACK_CMD
#
# Optional:
# - RATE_LIMIT_TIGHTEN_CMD
# - RATE_LIMIT_RELAX_CMD

fail=0

require_non_empty() {
  local name="$1"
  local value="${!name:-}"
  if [[ -z "$value" ]]; then
    echo "::error::missing required env: $name"
    fail=1
  else
    echo "[control-plane] $name configured."
  fi
}

require_non_empty "PROMETHEUS_BASE_URL"
require_non_empty "CANARY_SET_WEIGHT_CMD"
require_non_empty "CANARY_PROMOTE_CMD"
require_non_empty "CANARY_ROLLBACK_CMD"

if [[ "${CANARY_SET_WEIGHT_CMD:-}" != *'$WEIGHT'* ]]; then
  echo "::error::CANARY_SET_WEIGHT_CMD must reference \$WEIGHT"
  fail=1
fi

if [[ -n "${RATE_LIMIT_TIGHTEN_CMD:-}" ]]; then
  echo "[control-plane] RATE_LIMIT_TIGHTEN_CMD configured."
else
  echo "[control-plane] RATE_LIMIT_TIGHTEN_CMD not configured (optional)."
fi

if [[ -n "${RATE_LIMIT_RELAX_CMD:-}" ]]; then
  echo "[control-plane] RATE_LIMIT_RELAX_CMD configured."
else
  echo "[control-plane] RATE_LIMIT_RELAX_CMD not configured (optional)."
fi

if [[ "$fail" -ne 0 ]]; then
  echo "[control-plane] validation failed."
  exit 1
fi

echo "[control-plane] validation passed."
