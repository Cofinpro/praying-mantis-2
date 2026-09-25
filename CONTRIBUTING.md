# Contributing: Pull Request Policy

Every change to `main` goes through a pull request (PR). This policy implements the "Branch/PR rules" item of **T-0.1** in `plan.md`: story branch, 1 review, green build. Architecture choices live in `decisions.md`, and this document refers to them as D1, D2, ...

## 1. Branches

- `main` is protected: no direct pushes and no force-pushes.
- **One story = one branch = one PR.** Branch from `main` and name the branch after the Jira key, the area and a short description:
  - `SCRUM-12-be-login`
  - `SCRUM-14-fe-login-page`
  - `SCRUM-10-t-auth-contract`, for `together` stories that change `api/openapi.yaml`
- Keep branches short-lived. Stories are sized S or M; split an L story. Merge `main` into your branch often.

## 2. Commits and PR titles

- The **PR title** starts with the Jira key and follows [Conventional Commits](https://www.conventionalcommits.org/). With squash merge, the title becomes the commit on `main`:
  ```
  feat(absences): SCRUM-21 request an absence
  fix(timesheets): SCRUM-33 reject week_start that isn't a Monday
  ```
- Commits inside the branch can be informal, because they are squashed.

## 3. Opening a PR

- Fill in the PR template completely, including the Jira link and story ID (`BE-3.1`, `FE-3.2`, `T-3.0`).
- **Size:** aim for under 400 changed lines, not counting generated code, lockfiles or Liquibase seed data.
- **One concern per PR.** Don't mix refactors with features.
- Open it as a **Draft** until it's ready. Marking it "Ready for review" starts the AI reviewers.
- **Contract first (D2):** a feature's `api/openapi.yaml` change is merged in its own `T` story PR before the BE and FE implementation PRs.

## 4. Review process

| Changed path    | AI reviewer                         | Human approval needed                           |
|-----------------|-------------------------------------|-------------------------------------------------|
| `backend/**`    | `be-specialist`                     | BE code owner                                   |
| `frontend/**`   | `fe-specialist`                     | FE code owner                                   |
| `api/**`        | both                                | **both** BE and FE (D2)                         |
| more than one   | each matching reviewer              | each matching owner                             |

1. **The AI reviews run automatically** when a PR opens, gets new commits, or is marked ready.
2. The author must address every 🔴 **Blocker** and 🟠 **Major** finding, either by fixing it or by replying with a justification.
3. **At least 1 human approval** from the relevant code owner is required. Contract changes need both devs.
4. Reviewers should respond within **1 business day**.
5. The AI review supports human review. It does not replace it.
6. **This is a learning project.** The AI reviewers act as mentors: each finding explains the framework concept behind it, and 📚 **Learning** notes point out useful patterns even when nothing is wrong. Ask follow-up questions in the thread. Each review ends with suggested `learnings.md` entries. Add the useful ones at the demo and reflect step.

### Severity levels (shared by humans and AI)

- 🔴 **Blocker:** bug, security issue, data loss, broken contract, a hand-edited generated file, or an edited Liquibase changeset that has already run. Must fix.
- 🟠 **Major:** a deviation from an Accepted decision, missing tests for new logic, or a design or performance issue that will hurt soon. Fix, or justify and get agreement.
- 🟡 **Minor:** readability or small improvement. Author decides.
- 💬 **Nit / Question:** optional. This also covers deviations from a *Proposed* decision.
- 📚 **Learning:** no action needed. A lesson about the framework.

## 5. Merge requirements (enforced by branch protection)

- [ ] CI green: `./mvnw verify` (including Testcontainers) and `pnpm lint && pnpm test && pnpm build`, per BE-0.3 and FE-0.3, with generated code up to date with `openapi.yaml`
- [ ] Tests included for new logic (D10)
- [ ] AI review checks completed
- [ ] ≥ 1 approval from a code owner (both BE and FE for `api/**`)
- [ ] All conversations resolved
- [ ] Branch up to date with `main`
- [ ] Stale approvals dismissed on new commits

**Merge strategy:** squash merge only. The PR title becomes the commit message. The branch is deleted automatically after merging.

## 6. Architecture changes

If a PR makes or changes an architectural or tooling choice, update `decisions.md` in the same PR. Add a new entry, or move a *Proposed* one to *Accepted*.

## 7. Hotfixes

Use a `SCRUM-<n>-hotfix-<desc>` branch with the same rules. The review SLA drops to 2 hours. An admin may bypass the rules only during a declared incident, and a follow-up PR with tests and the root cause must be opened within 24h.
