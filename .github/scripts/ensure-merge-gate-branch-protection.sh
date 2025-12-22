#!/usr/bin/env bash
set -euo pipefail

# Enforce branch protection to require only "Merge Gate" as required status check.
# Usage:
#   GITHUB_TOKEN=... .github/scripts/ensure-merge-gate-branch-protection.sh owner/repo
#   GITHUB_TOKEN=... BRANCHES="main develop release" .github/scripts/ensure-merge-gate-branch-protection.sh owner/repo
#
# Notes:
# - Requires an admin-scoped token for the target repository.
# - This script updates protection for each branch in BRANCHES.

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required."
  exit 1
fi

repo="${1:-${GITHUB_REPOSITORY:-}}"
if [[ -z "$repo" ]]; then
  echo "Repository is required. Pass owner/repo or set GITHUB_REPOSITORY."
  exit 1
fi

if [[ -z "${GITHUB_TOKEN:-}" && -z "${GH_TOKEN:-}" ]]; then
  echo "GITHUB_TOKEN (or GH_TOKEN) is required."
  exit 1
fi

branches="${BRANCHES:-main develop}"

read -r -d '' payload <<'JSON' || true
{
  "required_status_checks": {
    "strict": true,
    "contexts": ["Merge Gate"]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": false,
    "required_approving_review_count": 1
  },
  "restrictions": null,
  "required_linear_history": false,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "block_creations": false,
  "required_conversation_resolution": true,
  "lock_branch": false,
  "allow_fork_syncing": true
}
JSON

for branch in $branches; do
  echo "Applying branch protection for $repo:$branch"
  gh api \
    --method PUT \
    -H "Accept: application/vnd.github+json" \
    "/repos/$repo/branches/$branch/protection" \
    --input <(printf '%s' "$payload")

  echo "Verifying required checks for $repo:$branch"
  gh api \
    -H "Accept: application/vnd.github+json" \
    "/repos/$repo/branches/$branch/protection/required_status_checks" \
    --jq '.contexts'
done

echo "Branch protection has been updated."
