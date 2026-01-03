# Flyway Migration Compatibility Strategy

This project uses an explicit expand/contract policy for DB migrations.

## 1. Policy

- Every migration `Vx__*.sql` must have metadata file: `backend/src/main/resources/db/migration/meta/Vx.yml`.
- CI validates metadata and risk level via `scripts/flyway/check-migration-policy.mjs`.
- Risky SQL (`DROP TABLE`, `DROP COLUMN`, `TRUNCATE`, destructive `ALTER TYPE`, bulk `DELETE`) must be marked:
  - `strategy: contract`
  - `backward_compatible: false`
  - rollback plan must explicitly mention backup restore.
- `contract` migrations must explicitly point to their compatible `expand` stage:
  - `paired_expand: V<version>`
  - paired migration must exist, be earlier version, and have expand-like strategy (`expand/index_only/data_backfill/init`).

## 2. Metadata format

Example:

```yaml
strategy: expand
backward_compatible: true
owner: backend-platform
rollout: add nullable column + backfill job
rollback: drop new column if unused
compat_window: one release
backfill_task: scripts/flyway/backfill-example.sh
```

Fields:
- `strategy`: `init | expand | contract | index_only | data_backfill`
- `backward_compatible`: `true | false`
- `owner`: owning team or role
- `rollout`: rollout approach
- `rollback`: rollback approach
- `compat_window`: expected compatibility window
- `paired_expand`: required for `contract`, points to the previous expand stage (e.g. `V6`)
- `backfill_task`: required for `data_backfill`, recommended when rollout contains backfill

## 3. Release playbook

For `expand`:
- release N: add schema/table/index, app writes/reads both if needed
- release N+1: switch read path fully to new schema

For `contract`:
- release N: ensure application is already compatible with target schema
- release N+1: apply contract migration
- rollback path must be backup-restore based unless reversible SQL is proven
- metadata must include `paired_expand` and non-empty `compat_window`

## 4. CI gate

Run locally:

```bash
node scripts/flyway/check-migration-policy.mjs
```

CI also runs this gate and blocks merge on violation.
