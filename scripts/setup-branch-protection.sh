#!/usr/bin/env bash
# Applies the PR policy from CONTRIBUTING.md to a GitHub repo.
# Usage: ./scripts/setup-branch-protection.sh <owner>/<repo>
# Requires: gh CLI authenticated with admin rights on the repo.
set -euo pipefail

REPO="${1:?usage: $0 <owner>/<repo>}"

echo "Configuring merge settings for $REPO..."
gh api -X PATCH "repos/$REPO" \
  -F allow_squash_merge=true \
  -F allow_merge_commit=false \
  -F allow_rebase_merge=false \
  -F delete_branch_on_merge=true \
  -f squash_merge_commit_title=PR_TITLE \
  -f squash_merge_commit_message=PR_BODY >/dev/null

echo "Protecting main..."
gh api -X PUT "repos/$REPO/branches/main/protection" --input - >/dev/null <<'JSON'
{
  "required_status_checks": null,
  "enforce_admins": false,
  "required_pull_request_reviews": {
    "required_approving_review_count": 1,
    "require_code_owner_reviews": true,
    "dismiss_stale_reviews": true,
    "require_last_push_approval": true
  },
  "required_conversation_resolution": true,
  "restrictions": null,
  "allow_force_pushes": false,
  "allow_deletions": false
}
JSON

echo "Done. Once the CI pipeline exists (BE-0.3 / FE-0.3), add its checks as required status checks."
