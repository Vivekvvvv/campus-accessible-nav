#!/usr/bin/env bash
set -euo pipefail

# Minimal staging deploy wrapper.
# Required env:
#   STAGING_DEPLOY_CMD   - shell command to perform deployment
# Optional env:
#   STAGING_SMOKE_CMD    - shell command for post-deploy smoke check
#   BACKEND_IMAGE_TAG    - image tag context for logging
#   FRONTEND_IMAGE_TAG   - image tag context for logging

if [[ -z "${STAGING_DEPLOY_CMD:-}" ]]; then
  echo "[staging] missing STAGING_DEPLOY_CMD"
  exit 1
fi

echo "[staging] backend image: ${BACKEND_IMAGE_TAG:-unknown}"
echo "[staging] frontend image: ${FRONTEND_IMAGE_TAG:-unknown}"
echo "[staging] running deploy command..."
bash -lc "${STAGING_DEPLOY_CMD}"

if [[ -n "${STAGING_SMOKE_CMD:-}" ]]; then
  echo "[staging] running smoke command..."
  bash -lc "${STAGING_SMOKE_CMD}"
fi

echo "[staging] deploy completed"
