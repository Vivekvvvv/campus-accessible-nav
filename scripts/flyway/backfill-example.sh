#!/usr/bin/env bash
set -euo pipefail

# Example backfill task script for expand/contract releases.
# Copy this file and adapt for a concrete migration, then reference it in
# migration meta as `backfill_task: scripts/flyway/<your-script>.sh`.

DB_URL="${DB_URL:-}"
DB_USER="${DB_USER:-}"
DB_PASSWORD="${DB_PASSWORD:-}"

if [[ -z "$DB_URL" || -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "Missing DB_URL/DB_USER/DB_PASSWORD"
  exit 1
fi

echo "[backfill-example] starting dry-run safe backfill..."
echo "[backfill-example] implement id-range batching + retry + idempotency guard before production usage."
echo "[backfill-example] done."
