## Story
<!-- Jira link and story ID from plan.md, e.g. SCRUM-21 · BE-3.1 -->
Jira: https://preying-mantis-2.atlassian.net/browse/SCRUM-
Story:

## What & why
<!-- What does this PR change and why? -->

## Type
- [ ] feat
- [ ] fix
- [ ] refactor
- [ ] chore / docs / test

## Area
- [ ] Backend (`backend/`)
- [ ] Frontend (`frontend/`)
- [ ] API contract (`api/openapi.yaml`): needs both BE and FE review

## How to test
<!-- Which automated tests cover this, and any manual steps for the reviewer. -->

## Screenshots / recordings (FE changes)

## Checklist
- [ ] PR is under 400 lines changed (not counting generated code or lockfiles), or I explained why not
- [ ] Tests added or updated: Testcontainers or unit tests (BE), Vitest with MSW (FE) (D10, D4)
- [ ] Follows `decisions.md`, or updates it in this PR
- [ ] Generated code (OpenAPI) not edited by hand, and regenerated if `openapi.yaml` changed (D3)
- [ ] Schema changes are new Liquibase changesets; no changeset that already ran was edited (D23)
- [ ] Errors are Problem Details with the agreed status codes (D21)
- [ ] Permissions enforced in the backend (D11)
- [ ] Frontend uses pnpm only (D5)
- [ ] No secrets, credentials, or PII committed
- [ ] Accessibility checked: keyboard, labels, contrast (FE)
- [ ] AI review findings addressed (🔴/🟠 fixed or justified)
