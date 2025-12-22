# Merge Rule (Branch Protection)

Use this repository rule for `main` and `develop`:

1. Require pull request before merging.
2. Require status checks to pass before merging.
3. Required status check: `Merge Gate`.
4. Require branches to be up to date before merging.
5. Dismiss stale pull request approvals when new commits are pushed.

`Merge Gate` is defined in `.github/workflows/ci.yml` and enforces:

- `backend` (`mvn clean verify`)
- `backend-it` (`mvn -Pit verify`)
- `api-contract` (OpenAPI breaking-change gate on PR)
- `flyway-policy` (migration metadata/compat policy gate)
- `frontend` (`openapi types diff + lint + unit + build + perf budget`)
- `security-policy` (allowlist expiry/reason validation)
- `security` (Trivy fail gate)
- Pull request only: `e2e` and `osv`

This avoids relying on manual memory of individual checks.

Apply/update branch protection via script (admin token required):

```bash
export GITHUB_TOKEN=<admin_token>
export BRANCHES="main develop"
.github/scripts/ensure-merge-gate-branch-protection.sh <owner>/<repo>
```
