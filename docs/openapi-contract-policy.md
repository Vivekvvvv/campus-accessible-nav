# OpenAPI Contract Policy

## 1. Goal

Prevent unintentional API breaking changes from being merged.

## 2. CI gate

PR pipeline runs `api-contract` job in `.github/workflows/ci.yml`.

Implementation:
- generate `new` OpenAPI from PR HEAD backend (`/v3/api-docs`)
- generate `old` OpenAPI from PR base SHA backend
- run `scripts/openapi/check-breaking.mjs --old <old> --new <new>`

## 3. Breaking rules (current)

The gate fails when it detects:

- removed path
- removed HTTP operation on existing path
- request body becomes required
- new required parameter
- optional parameter becomes required
- removed 2xx response codes
- removed components schema

## 4. Exception process

If a breaking change is intentional, explicitly set:

- `ALLOW_OPENAPI_BREAKING=true` in CI context for that PR only (temporary),
- and include migration/release notes plus client rollout plan.

Default policy is fail-closed.
