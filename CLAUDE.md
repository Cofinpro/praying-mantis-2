# praying-mantis-1

Coding dojo project: a springboot backend and a vue frontend.

The full plan (data model, decisions, user stories, milestones) is in `plan.md`. The stories are tracked in Jira: project **SCRUM** ("Preying Mantis 2") at https://preying-mantis-2.atlassian.net

## Project explanation

We are building an internal company platform that brings existing systems together in one place. Its features:

| Feature | Owner | In scope? |
|---|---|---|
| Book trainings | another group | ❌ (a nav link at most) |
| Reserve seats in the office | another group | ❌ (a nav link at most) |
| Timesheets | us | ✅ |
| Vacations | us | ✅ |
| Expense sheets | nobody yet |  |

**Tech stack:**
- **Backend:** Java, openAPI, Postgres
- **Frontend:** Vue + TypeScript on Vite, with pnpm

A second goal is **learning** these technologies, so we prefer the approach that teaches something over a shortcut that hides it.

We are **experienced developers**, new to this stack: one comes from **Vue**, the other from **Java**. Skip general frontend/backend basics in explanations and in `learnings.md`. Focus on what's specific to Vue, Java, springboot, postgres.

## Team and workflow

Three developers:
- **BE dev**: backend and database. Stories are `BE-x.y` and have the Jira label `backend`.
- **FE dev**: frontend. Stories are `FE-x.y` and have the Jira label `frontend`.

For every feature:
1. **Together**: agree the questions, the screens and the **API contract** (endpoints, JSON, error codes). Jira label: `together`. The contract PR merges once CI is green, without waiting for the other dev (D30); adjustments go in a follow-up PR.
2. **In parallel**: BE implements the contract. FE builds against MSW mocks that follow the contract.
3. **Integrate**: FE switches from the mocks to the real API.
4. **Demo and reflect**: add entries to `learnings.md`.

Each story gets its own branch and PR (e.g. `SCRUM-12-be-login`).

## Data model

This is the target model, a refined version of our first sketch (see `plan.md` §4 for the reasons). Tables are snake_case and plural.

- **users**: name, email (unique), password_hash, client (DKB|Deka|VV|DBIS|UNION), level (junior|expert|senior|architect|senior_architect), is_admin, team_lead_id → users (nullable)
	"Is team lead" is derived (someone has you as team_lead_id), not stored
	"Privileged account" = is_admin
- **notifications**: user_id, type, message, link, read_at, created_at
seats: label (unique, e.g. DKB-03), zone (same Client enum as users), pos_x and pos_y (grid position on the map)
- **absence_types**: code (unique, e.g. VACATION|SICK|PARENTAL|UNPAID|TRAINING), name, is_paid, deducts_from_balance, requires_approval
- **absence_entitlements**: user_id, absence_type_id, year, entitled_days, carried_over_days UNIQUE (user_id, absence_type_id, year)
- **absence_requests**: user_id, absence_type_id, start_date, end_date, start_part and end_part (full|morning|afternoon), working_days, status (pending|approved|rejected|cancelled), reason, approver_id → users, decided_at
	The approver is normally the user's team lead
	A user's pending or approved requests can't overlap
	Remaining days = entitled + carried over − approved working_days (computed, not stored)
- **projects**: code (unique), name, client (same Client enum, nullable = internal), is_billable, is_active
- **timesheets**: user_id, week_start (a Monday), status (draft|submitted|approved|rejected), approver_id → users, decided_at UNIQUE (user_id, week_start): one timesheet per person per week
- **time_entries**: timesheet_id, project_id, work_date, hours, description

## Feature workflow

### Absence scheduling

1. A user logs in
2. He can see his Vacation balance and a calendar with his Absences.
3. He decides to book a new Absence.
4. Submits the request.
5. His assigned team leader receives a notification.
6. The team leader can decide to approve or deny the request.
7. After the team leader's decision, the user gets a notification and his board gets updated.

Status flow: `pending → approved | rejected`; `pending | approved → withdrawn`.

### Timesheet exporting

1. For each month, the user has a button where he can export his timesheet.
2. He can select from a list of templates, based on the list of clients.
3. The worksheet is exported into an excel.

## Layout

What exists today:
- `backend/`: OpenAPI + Java springboot
- `frontend/`: Vue + TypeScript on Vite, managed with **pnpm**
- `api/`: openAPI files
- Hosting (decision #30): `.github/workflows/pages.yml` publishes a mock-mode frontend demo to GitHub Pages; `render.yaml` + `backend/Dockerfile` run the backend and Postgres on Render

## Conventions

- Use pnpm for the frontend, never npm or yarn.
- **API:**
  - REST + JSON, all routes under /api
- Contract-first: openapi.yaml in the repo is the source of truth, merged in its `T` story PR before implementing (D30: it doesn't wait for the other dev)
- **Backend:**
  - DB access goes through Spring Data JPA
  - Business rules live in @Service classes, not in @RestControllers
  - Every schema change needs a Liquibase changeset (src/main/resources/db/changelog, no ddl-auto=update)
  - Enums are stored as VARCHAR (@Enumerated(EnumType.STRING))
  - Controllers implement the interfaces generated from openapi.yaml (openapi-generator-maven-plugin)
  - Tests use JUnit 5 + @SpringBootTest against a real Postgres test database (Testcontainers)
- **Frontend:**
  - Vue Router for pages, TanStack Query (@tanstack/vue-query) for server data, <style scoped> for styles
  - src/api/client.ts is the only code that talks to the API
  - TypeScript API types are generated from openapi.yaml (openapi-typescript)
  - **UI follows the Figma file** (D24): https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ. Before starting any `FE-x.y` story, find its frame in `design.md` and build what it shows (layout, states, copy). No frame yet → design it in Figma first. Implementation must differ → update Figma first.
  - Styles use the design tokens (CSS variables in `src/assets/tokens.css`, listed in `design.md`), never raw hex colours, font names or radii
- **Permissions:** the backend enforces all of them. Hiding buttons in the UI is only a convenience.
- **Docs:**
  - Record architectural or tooling choices in `decisions.md`
  - `design.md` summarises the Figma file (frames per story, tokens, components). Keep it in sync with Figma in the same PR
  - Record useful things to learn in `learnings.md`, grouped by topic (Vue, Java, Springboot, Postgres,..) since we're here to learn new tech
## Pull requests and AI reviewers

- PR policy: see `CONTRIBUTING.md`. One story per branch and PR (`SCRUM-12-be-login`), green build, squash merge. No approval needed to merge (D27); ask a code owner when you want a review.
- Every PR is reviewed by an AI mentor in `.claude/agents/`: `be-specialist` for `backend/**`, `fe-specialist` for `frontend/**`, and both for `api/**` (contract changes). The reviews run locally via `/review-pr` (`--post` puts them on the PR). There is no GitHub Action for this.
- The reviewers follow `decisions.md` first, then this file, `api/openapi.yaml` and (for `fe-specialist`) `design.md`, then `.claude/skills/`. Where a skill disagrees with a decision (e.g. Flyway vs Liquibase, Pinia vs TanStack Query for server data), the decision wins.
- They teach as they review and suggest entries for `learnings.md`.
