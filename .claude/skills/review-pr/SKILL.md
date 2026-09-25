---
name: review-pr
description: Run the be-specialist / fe-specialist mentor reviews locally on the current branch, and optionally post the review to its GitHub PR. Use when the user types /review-pr or asks to review their branch before or during a PR.
argument-hint: "[--post] [base-branch]"
disable-model-invocation: true
---

# Local AI mentor review

Reviews the current branch with the project's reviewer subagents. It runs locally in Claude Code, so it needs no API key and no GitHub Action.

Arguments: `$ARGUMENTS`
- `--post`: after the review, offer to post it as a comment on the branch's open PR.
- Any other word is the base branch. The default is `main`.

## Steps

1. **Work out what changed.**
   - Run `git fetch origin <base>`. If it fails (offline or not logged in), fall back to the local `<base>` and say so.
   - Collect the changed files: `git diff --name-only origin/<base>...HEAD`, plus uncommitted changes from `git status --porcelain`.
   - If nothing changed, say so and stop.
2. **Pick the reviewers** (same routing as `CONTRIBUTING.md`):
   - `backend/**` or `api/**` changed → `be-specialist`
   - `frontend/**` or `api/**` changed → `fe-specialist`
   - Neither → say that no specialist review applies (e.g. docs-only changes) and stop.
3. **Run the reviewers.** Use the Agent tool with `subagent_type` set to each chosen reviewer. **If both apply, launch them in parallel in one message.** Give each one:
   - the base ref and the command to see the diff: `git diff origin/<base>...HEAD -- <its paths>`. Include uncommitted changes if there are any.
   - the list of changed files in its scope
   - an instruction to follow its own sources of truth, checklist and output format, and to return the full review as markdown
4. **Show the reviews** to the user in full, one section per reviewer, keeping each agent's formatting. Then add a short combined list of what must be fixed before merging (🔴 and 🟠 only).
5. **If `--post` was given:**
   - Find the PR with `gh pr view --json number,url` (use the `gh` CLI; if it isn't on `PATH`, try `/opt/homebrew/bin/gh`). If there's no open PR for the branch, say so and stop.
   - **Ask the user to confirm** before posting, and show the PR number.
   - Post one comment per reviewer with `gh pr comment <number> --body-file <file>`. Write the body to a temp file first, and start it with a heading such as `## 🤖 BE specialist review (local)`.
   - Report the comment URLs.

## Notes
- Never post without the user's confirmation.
- Don't edit code as part of this review. The author decides what to fix.
