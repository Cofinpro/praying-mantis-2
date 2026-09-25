# Contributing — Merge Request (PR) Policy

On GitHub, merge requests are called **Pull Requests (PRs)**. Every change to `main` goes through one.

## 1. Branching

- `main` is protected: no direct pushes and no force-pushes.
- Branch from `main` and name the branch `<type>/<ticket>-<short-description>`:
  - `feat/ABC-123-user-login`
  - `fix/ABC-456-null-price`
  - `chore/`, `refactor/`, `docs/`, `test/`
- Keep branches short-lived (ideally under 3 days). Rebase on `main` often.

## 2. Commits

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(api): add pagination to /orders
fix(ui): prevent double submit on checkout form
```

## 3. Opening a PR

- **Title** follows Conventional Commits (`feat(scope): ...`).
- Fill in the PR template completely.
- **Size:** aim for under 400 changed lines. Split anything bigger unless it's generated code or a migration.
- **One concern per PR.** Don't mix refactors with features.
- Open it as a **Draft** until it's ready. Marking it "Ready for review" starts the AI reviewers.
- Link the ticket (`Closes ABC-123`).
- **No automated tests in this project**, so the "How to test" section is mandatory: give manual steps covering the happy path and the edge cases. FE changes must include screenshots or recordings.

## 4. Review process

| Changed path  | AI reviewer        | Human code owner |
|---------------|--------------------|------------------|
| `backend/**`  | `be-specialist`    | `@Cofinpro/backend`   |
| `frontend/**` | `fe-specialist`    | `@Cofinpro/frontend`  |
| both          | both run           | both teams       |

1. **The AI reviews run automatically** when a PR opens, gets new commits, or is marked ready.
2. The author must address every 🔴 **Blocker** and 🟠 **Major** finding, either by fixing it or by replying with a justification.
3. **At least 1 human approval** from the relevant code owner is required. If a PR touches both BE and FE, it needs an approval from each team.
4. Reviewers should respond within **1 business day**.
5. The AI review supports human review. It does not replace it.
6. **This is a learning project.** The AI reviewers also act as mentors: each finding explains the framework concept behind it, and 📚 **Learning** notes point out useful patterns even when nothing is wrong. Read them, and ask follow-up questions in the thread.

### Severity levels (shared by humans and AI)

- 🔴 **Blocker:** bug, security issue, data loss, or broken contract. Must fix.
- 🟠 **Major:** design or performance issue that will hurt soon. Fix, or justify and get agreement.
- 🟡 **Minor:** readability or small improvement. Author decides.
- 💬 **Nit / Question:** optional.
- 📚 **Learning:** no action needed. A lesson about the framework.

## 5. Merge requirements (enforced by branch protection)

- [ ] CI green (backend `./mvnw verify`, frontend `npm run build`, which includes the `vue-tsc` type-check)
- [ ] AI review checks completed
- [ ] ≥ 1 approval from a code owner
- [ ] All conversations resolved
- [ ] Branch up to date with `main`
- [ ] Stale approvals dismissed on new commits

**Merge strategy:** squash merge only. The PR title becomes the commit message. The branch is deleted automatically after merging.

## 6. Hotfixes

Use a `hotfix/` branch with the same rules. The review SLA drops to 2 hours. An admin may bypass the rules only during a declared incident, and a follow-up PR documenting the root cause must be opened within 24h.
