# praying-mantis-1 — Plan

Internal company platform that brings existing systems together in one place. We own **Vacations (absences)** and **Timesheets**. Trainings and seat reservation belong to other groups and only appear as nav links.

This file is the source for the Jira backlog (project **SCRUM**). Each story below is written so it can be copied into a Jira issue as-is: title, user story, acceptance criteria, label, dependencies.

---

## 1. How to read this plan

**Story IDs**

| Prefix | Meaning | Jira label |
|---|---|---|
| `T-x.y` | Done together: questions, screens, API contract | `together` |
| `BE-x.y` | Backend and database | `backend` |
| `FE-x.y` | Frontend | `frontend` |

`x` is the epic number (section 6), `y` the story within it. The Jira key (e.g. `SCRUM-12`) is assigned on creation; the branch is named `SCRUM-12-be-login`.

**Per feature:** `T` story first (contract merged into `api/openapi.yaml`) → `BE` and `FE` stories in parallel (FE against MSW mocks) → integration story → demo and `learnings.md` entries.

**Size:** S (≤ ½ day), M (1–2 days), L (3+ days, consider splitting).

---

## 2. Scope

| Feature | In scope | Notes |
|---|---|---|
| Login and user profile | ✅ | Needed by everything else |
| Absences (vacation, sick, …) | ✅ | Balance, calendar, request, approval |
| Notifications | ✅ | In-app only, no e-mail |
| Timesheets | ✅ | Weekly entry, submit, approval, monthly Excel export |
| Admin (users, entitlements, projects) | ✅ (late) | Seed data covers us until then |
| Book trainings | ❌ | Nav link only |
| Reserve seats | ❌ | Nav link only |
| Expense sheets | ❌ | No owner yet |

---

## 3. Decisions (proposed — move to `decisions.md` once agreed)

| # | Decision | Why |
|---|---|---|
| D1 | **Authentication: Spring Security with a session cookie** (HttpOnly, `SameSite=Lax`), login via `POST /api/auth/login`. Passwords hashed with BCrypt. Vite dev server proxies `/api` so FE and BE share an origin. | Teaches the Spring Security filter chain without the extra complexity of token refresh. JWT can be a later learning story if we want it. |
| D2 | **Absence cancel status is `cancelled`.** CLAUDE.md used both `withdrawn` and `cancelled`. | One name everywhere (DB, API, UI). |
| D3 | **No overlap between a user's pending/approved requests**, enforced in the service *and* by a Postgres exclusion constraint (`btree_gist` + `daterange`). | Service gives a nice 409 error; the constraint guarantees it even under races. Good Postgres learning. |
| D4 | **Working days exclude weekends and public holidays**; half days count as 0.5. New table `public_holidays`. | Without holidays the balance is wrong around Christmas/Easter. |
| D5 | **Approver fallback:** if a user has no team lead, requests go to admins. A team lead's own requests go to *their* team lead. | Nobody's request can get stuck. |
| D6 | **The frontend polls for new notifications.** The bell asks `GET /api/me/notifications/unread-count` every 30 s (TanStack Query `refetchInterval`, plus an automatic refetch when the browser tab regains focus). The server never pushes anything. | Plain REST, nothing extra to build or learn on day one. Cost: a new notification can take up to 30 s to show, and each open tab sends a tiny request every 30 s, which is fine for an internal tool. Server push (Server-Sent Events or WebSockets) stays a stretch story. |
| D7 | **Monthly export aggregates weekly timesheets**: all entries with `work_date` in the month, regardless of which week's timesheet they belong to. | Timesheets are weekly, exports are monthly; weeks cross month borders. |
| D8 | **Export templates live in the backend code base**: one per client plus a generic one. Each template is an `.xlsx` file in `src/main/resources/export-templates/` (the client's own layout) plus a Java class that fills in its cells, using Apache POI (the standard Java library for reading and writing Excel files). Not stored in the DB. | Templates change rarely and need code anyway; keeping the client's real file means formatting and logos come for free; no admin UI needed. |
| D9 | **Seats table removed** from our model. | Seat reservation belongs to another group. |
| D10 | **Errors use RFC 7807 Problem Details** (`application/problem+json`, Spring's `ProblemDetail`). | Standard, built into Spring Boot 3, one error shape for FE to handle. |
| D11 | **Dates are ISO `YYYY-MM-DD`, timestamps ISO-8601 UTC.** Week start is always a Monday. | Avoids timezone surprises in calendars. |
| D12 | **Schema migrations with Liquibase** (replaces Flyway). Master changelog `src/main/resources/db/changelog/db.changelog-master.yaml` includes one YAML changelog per story. Postgres-specific parts (exclusion constraint, `CHECK`s) use the `sql` change type. Dev seed data uses changesets with `context: dev`. `ddl-auto=validate`. | Team choice. Liquibase tracks changesets by id/author/checksum, supports rollbacks and contexts; YAML changesets teach the Liquibase model, `sql` keeps full Postgres power. |

---

## 4. Data model

Tables are snake_case and plural. Every table has `id BIGSERIAL PK`; `created_at`/`updated_at` where useful. Enums are `VARCHAR` with a `CHECK` constraint.

**users**
- `name`, `email` (unique), `password_hash`
- `client` (DKB | Deka | VV | DBIS | UNION)
- `level` (junior | expert | senior | architect | senior_architect)
- `is_admin` — the "privileged account"
- `team_lead_id` → users (nullable)
- *Is team lead* is derived (someone has you as `team_lead_id`), not stored — avoids two sources of truth.

**notifications**
- `user_id`, `type` (e.g. ABSENCE_REQUESTED, ABSENCE_DECIDED, TIMESHEET_SUBMITTED, TIMESHEET_DECIDED), `message`, `link` (FE route), `read_at` (null = unread), `created_at`

**absence_types**
- `code` (unique: VACATION | SICK | PARENTAL | UNPAID | TRAINING), `name`, `is_paid`, `deducts_from_balance`, `requires_approval`
- Types that don't require approval (e.g. SICK) are created as `approved` directly.

**absence_entitlements**
- `user_id`, `absence_type_id`, `year`, `entitled_days` (numeric 4,1), `carried_over_days` (numeric 4,1)
- UNIQUE (`user_id`, `absence_type_id`, `year`)

**absence_requests**
- `user_id`, `absence_type_id`, `start_date`, `end_date`, `start_part` / `end_part` (full | morning | afternoon)
- `working_days` (numeric 4,1) — computed at creation (D4), stored so it doesn't change if holidays are edited later
- `status` (pending | approved | rejected | cancelled), `reason`, `approver_id` → users, `decided_at`, `decision_comment` *(new: lets the team lead explain a rejection)*
- Exclusion constraint: no overlapping date ranges per user for status pending/approved (D3)
- *Remaining days* = entitled + carried over − approved `working_days` of that type and year (computed, not stored)

**public_holidays** *(new, D4)*
- `date` (unique), `name`

**projects**
- `code` (unique), `name`, `client` (Client enum, nullable = internal), `is_billable`, `is_active`

**timesheets**
- `user_id`, `week_start` (CHECK: is a Monday), `status` (draft | submitted | approved | rejected), `approver_id` → users, `decided_at`, `decision_comment` *(new)*
- UNIQUE (`user_id`, `week_start`)
- A rejected timesheet can be edited again and resubmitted.

**time_entries**
- `timesheet_id`, `project_id`, `work_date` (within the timesheet's week), `hours` (numeric 4,2, > 0, ≤ 24), `description`
- Sum of hours per user per day ≤ 24 (service rule)

**Status flows**
- Absence: `pending → approved | rejected`; `pending | approved → cancelled` (approved only if the start date is in the future)
- Timesheet: `draft → submitted → approved | rejected`; `rejected → submitted`

---

## 5. API outline (draft — the `T` stories turn this into `api/openapi.yaml`)

All routes under `/api`. Errors: 400 validation, 401 not logged in, 403 not allowed, 404 not found, 409 conflict (overlap, wrong status).

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/login` | Log in (email, password) |
| POST | `/auth/logout` | Log out |
| GET | `/me` | Current user, incl. `isTeamLead`, `isAdmin` |
| GET | `/absence-types` | List types |
| GET | `/me/absence-balance?year=` | Entitled, carried over, used, pending, remaining per type |
| GET | `/me/absence-requests?from=&to=` | My requests in a range (calendar) |
| POST | `/me/absence-requests` | Create request (returns computed `workingDays`) |
| POST | `/me/absence-requests/{id}/cancel` | Cancel |
| GET | `/public-holidays?year=` | For the calendar and the day preview |
| GET | `/team/absence-requests?status=pending` | Requests I have to decide on |
| POST | `/team/absence-requests/{id}/approve` · `/reject` | Decide (optional comment) |
| GET | `/team/absences?from=&to=` | Team calendar (stretch) |
| GET | `/me/notifications?unread=` | List |
| GET | `/me/notifications/unread-count` | Badge |
| POST | `/me/notifications/{id}/read` · `/me/notifications/read-all` | Mark read |
| GET | `/projects?active=true` | Projects to book on |
| GET | `/me/timesheets/{weekStart}` | Get week (creates draft if missing) |
| PUT | `/me/timesheets/{weekStart}/entries` | Replace the week's entries |
| POST | `/me/timesheets/{weekStart}/submit` | Submit |
| GET | `/team/timesheets?status=submitted` | Timesheets I have to decide on |
| POST | `/team/timesheets/{id}/approve` · `/reject` | Decide |
| GET | `/export-templates` | List templates |
| GET | `/me/timesheet-exports?month=YYYY-MM&template=` | Download `.xlsx` |
| … | `/admin/users`, `/admin/entitlements`, `/admin/projects`, `/admin/public-holidays` | Admin CRUD (epic 9) |

---

## 6. User stories

### Epic 0 — Foundation

**T-0.1 Agree on repo conventions and local setup** · `together` · S
As the team, we want one agreed local setup so that everyone can run the whole stack in minutes.
- `docker-compose.yml` with Postgres for local dev
- README: how to start BE, FE, DB
- Branch/PR rules written down (story branch, 1 review, green build)
- `decisions.md` and `learnings.md` created with D1–D11 reviewed

**BE-0.1 Spring Boot skeleton with Postgres and Liquibase** · `backend` · M
As a BE dev, I want a running Spring Boot app connected to Postgres so that I can start adding features.
- Maven project in `backend/`, Java 21, Spring Boot 3 (web, data-jpa, validation, security, liquibase-core)
- Master changelog set up (D12); an empty initial changeset runs on start; `ddl-auto=validate`
- Health endpoint reachable (`/actuator/health`)
- One `@SpringBootTest` using Testcontainers Postgres passes

**BE-0.2 Generate server interfaces from openapi.yaml** · `backend` · S
As a BE dev, I want controller interfaces generated from `api/openapi.yaml` so that the code can't drift from the contract.
- `openapi-generator-maven-plugin` runs in `generate-sources` (interface-only, Spring, `useSpringBoot3`)
- A sample endpoint controller implements the generated interface
- Global `@RestControllerAdvice` returns Problem Details (D10)

**FE-0.1 Vue + Vite skeleton** · `frontend` · S
As a FE dev, I want a Vue + TypeScript app with routing and server-state set up so that I can start building pages.
- Vite + Vue 3 + TS in `frontend/`, managed with pnpm
- Vue Router, `@tanstack/vue-query` installed and wired
- ESLint + Prettier + Vitest configured
- Vite proxy `/api` → backend (D1)

**FE-0.2 Generated API types, API client and MSW** · `frontend` · M
As a FE dev, I want typed API calls and mocks so that I can build features before the backend is ready.
- `pnpm gen:api` runs `openapi-typescript` on `api/openapi.yaml`
- `src/api/client.ts` is the only module doing HTTP; handles 401 (redirect to login) and Problem Details errors
- MSW set up for dev (toggle via env var) and for Vitest

**BE-0.3 / FE-0.3 CI pipeline** · `backend` / `frontend` · S each
As the team, we want each PR built and tested automatically so that main stays green.
- Pipeline runs `mvn verify` (incl. Testcontainers) and `pnpm lint && pnpm test && pnpm build`
- Fails if generated code is out of date with `openapi.yaml`

---

### Epic 1 — Login and app shell

**T-1.1 Contract: auth and current user** · `together` · S
Agree screens (login, app shell) and the `/auth/*` and `/me` endpoints, incl. error responses. Merge into `openapi.yaml`.

**BE-1.1 Users table and seed data** · `backend` · S
As a BE dev, I want a users table with realistic seed data so that we can log in and test roles.
- Liquibase changeset for `users` (constraints from §4)
- Dev-only seed (changeset with `context: dev`): 1 admin, 2 team leads, ~5 employees across clients
- JPA entity with enums as `EnumType.STRING`

**BE-1.2 Login, logout, current user** · `backend` · M · depends on BE-1.1, T-1.1
As a user, I want to log in with my email and password so that I can access my data.
- `POST /api/auth/login` checks BCrypt hash, creates session; wrong credentials → 401 (no hint which field was wrong)
- `POST /api/auth/logout` invalidates session
- `GET /api/me` returns profile incl. derived `isTeamLead`
- All other `/api/**` routes require authentication
- Tests for success, wrong password, unauthenticated access

**BE-1.3 Authorization helpers** · `backend` · S · depends on BE-1.2
As a BE dev, I want reusable permission checks so that services enforce rules consistently.
- Helpers: `isAdmin`, `isTeamLeadOf(userId)`, `approverFor(user)` (incl. fallback D5)
- 403 as Problem Details

**FE-1.1 Login page** · `frontend` · S · depends on FE-0.2, T-1.1
As a user, I want a login page so that I can sign in.
- Email + password form, inline error on 401, disabled button while submitting
- On success, redirect to the page originally requested (or home)

**FE-1.2 App shell and route guard** · `frontend` · M · depends on FE-1.1
As a user, I want a consistent layout with navigation so that I can reach all features.
- Header with user name, notification bell placeholder, logout
- Nav: Absences, Timesheets, Approvals (only if team lead), Admin (only if admin), external links to Trainings and Seats
- Router guard redirects to login when `/me` returns 401

**FE-1.3 Integrate auth with the real backend** · `frontend` · S · depends on BE-1.2, FE-1.2
- MSW off, login/logout/me work end-to-end against the backend

---

### Epic 2 — Absences: balance and calendar

**T-2.1 Contract: absence types, balance, my requests, holidays** · `together` · M
Agree the Absences page (balance cards + calendar) and endpoints in §5.

**BE-2.1 Absence types, entitlements, holidays schema and seed** · `backend` · S
- Liquibase changesets for `absence_types`, `absence_entitlements`, `public_holidays`, `absence_requests` (exclusion constraint D3 via `sql` change)
- Seed: the five types, entitlements for seeded users for the current year, current and next year's holidays

**BE-2.2 My absence balance** · `backend` · M · depends on BE-2.1
As a user, I want to see how many vacation days I have left so that I can plan time off.
- `GET /api/me/absence-balance?year=` per type: entitled, carried over, used (approved), pending, remaining
- Only types with `deducts_from_balance` return remaining
- Tests incl. half days and requests crossing year boundaries (days count in the year they fall in)

**BE-2.3 My absence requests in a date range** · `backend` · S · depends on BE-2.1
- `GET /api/me/absence-requests?from=&to=` returns requests overlapping the range, all statuses
- `GET /api/public-holidays?year=`

**FE-2.1 Balance cards** · `frontend` · S · depends on T-2.1
As a user, I want my balance at the top of the Absences page so that I see it at a glance.
- One card per type with a balance; shows remaining, and pending as a secondary number
- Year selector

**FE-2.2 Absence calendar** · `frontend` · L · depends on T-2.1
As a user, I want a calendar of my absences so that I see what's booked.
- Month view; absences coloured by type, pending visually distinct (e.g. striped), rejected/cancelled hidden by default
- Weekends and public holidays marked
- Half days shown as half cells
- Decide together: own component vs library (record in `decisions.md`)

**FE-2.3 Integrate balance and calendar** · `frontend` · S · depends on BE-2.2, BE-2.3, FE-2.1, FE-2.2

---

### Epic 3 — Absences: request and cancel

**T-3.1 Contract: create and cancel absence request** · `together` · S
Agree the request form, validation errors (field-level 400) and 409 cases (overlap, insufficient balance?).
Open question to settle: **may a request exceed the remaining balance?** (Proposal: reject with 409.)

**BE-3.1 Working-days calculation** · `backend` · S
As a BE dev, I want a tested calculator so that balances are correct.
- Excludes weekends and `public_holidays`; `morning`/`afternoon` on start/end day count as 0.5
- Single-day request: start part and end part must be consistent
- Pure unit tests (no Spring context) with edge cases

**BE-3.2 Create absence request** · `backend` · M · depends on BE-3.1, BE-1.3
As a user, I want to request an absence so that my team lead can approve it.
- Validates dates (end ≥ start, working_days > 0), type exists, balance rule from T-3.1
- Overlap with own pending/approved → 409 (plus DB constraint as safety net)
- Sets `approver_id` via `approverFor` (D5); status `pending`, or `approved` if the type doesn't require approval
- Creates ABSENCE_REQUESTED notification for the approver (once epic 4 exists; stub until then)

**BE-3.3 Cancel absence request** · `backend` · S · depends on BE-3.2
As a user, I want to cancel a request so that I can change my plans.
- Only own requests; pending anytime, approved only if start date is in the future; otherwise 409
- Notifies the approver if the request was approved

**FE-3.1 Request absence form** · `frontend` · M · depends on T-3.1
As a user, I want a form to request time off so that it's quick to book.
- Type, start/end date, start/end part, reason
- Live preview of working days (computed client-side from holidays; backend value is authoritative)
- Shows backend validation and 409 errors inline; on success, balance and calendar refresh (query invalidation)

**FE-3.2 Cancel from calendar/list** · `frontend` · S · depends on T-3.1
- Clicking an absence opens details with a Cancel button when allowed; confirmation dialog

**FE-3.3 Integrate request and cancel** · `frontend` · S · depends on BE-3.2, BE-3.3, FE-3.1, FE-3.2

---

### Epic 4 — Notifications

**T-4.1 Contract: notifications** · `together` · S
Agree notification types, texts, links and the bell/dropdown UI.

**BE-4.1 Notification service and schema** · `backend` · S
- Liquibase changeset for `notifications`; `NotificationService.notify(user, type, message, link)` used by other services
- Called in the same transaction as the business change

**BE-4.2 Notification endpoints** · `backend` · S · depends on BE-4.1
As a user, I want to see and dismiss my notifications.
- List (newest first, paginated), unread count, mark one read, mark all read
- Users only ever see their own notifications

**FE-4.1 Notification bell** · `frontend` · M · depends on T-4.1
As a user, I want a bell with an unread badge so that I notice decisions and requests.
- Badge polls unread count (D6); dropdown lists recent notifications; clicking navigates to `link` and marks read; "mark all read"

**FE-4.2 Integrate notifications** · `frontend` · S · depends on BE-4.2, FE-4.1

---

### Epic 5 — Absences: team lead approval

**T-5.1 Contract: team approval** · `together` · S
Agree the Approvals page and approve/reject endpoints (reject comment required?).

**BE-5.1 Pending requests for my team** · `backend` · S · depends on BE-3.2
As a team lead, I want to see requests waiting for me so that I can decide on them.
- `GET /api/team/absence-requests?status=pending` returns requests where I'm the approver, incl. requester name, type, dates, working days, requester's remaining balance
- Non-approvers get an empty list

**BE-5.2 Approve or reject a request** · `backend` · M · depends on BE-5.1, BE-4.1
As a team lead, I want to approve or reject a request so that my team member knows the outcome.
- Only the assigned approver (or an admin); only `pending` requests, else 409
- Sets status, `decided_at`, optional `decision_comment`
- Notifies the requester (ABSENCE_DECIDED)

**FE-5.1 Approvals page (absences)** · `frontend` · M · depends on T-5.1
As a team lead, I want an Approvals page so that I can decide quickly.
- List of pending requests with Approve / Reject (reject opens comment dialog)
- Optimistic update or refetch after decision
- Page only in nav for team leads; direct access by others shows "nothing to approve" (backend enforces anyway)

**FE-5.2 Integrate approvals** · `frontend` · S · depends on BE-5.2, FE-5.1

**BE-5.3 / FE-5.3 Team calendar** *(stretch)* · `backend` / `frontend` · M each
As a team lead, I want to see my team's absences in one calendar so that I can spot conflicts before approving.

---

### Epic 6 — Timesheets: entry and submit

**T-6.1 Contract: projects and weekly timesheet** · `together` · M
Agree the week grid screen (rows = projects, columns = days), entries payload, validation and status rules.

**BE-6.1 Projects schema, seed and list** · `backend` · S
- Liquibase changeset for `projects`; seed a few billable client projects and internal ones (e.g. "Internal", "Training")
- `GET /api/projects?active=true`

**BE-6.2 Get or create my weekly timesheet** · `backend` · M · depends on BE-6.1
As a user, I want to open any week so that I can record my hours.
- Liquibase changesets for `timesheets`, `time_entries`
- `GET /api/me/timesheets/{weekStart}`: 400 if not a Monday; creates a `draft` if none exists; returns entries plus the week's approved absences (read-only info)

**BE-6.3 Save weekly entries** · `backend` · M · depends on BE-6.2
As a user, I want to save my hours for the week.
- `PUT .../entries` replaces all entries of the week in one transaction
- Only when status is `draft` or `rejected`, else 409
- Validates: dates inside the week, active project, 0 < hours ≤ 24, daily total ≤ 24

**BE-6.4 Submit timesheet** · `backend` · S · depends on BE-6.3, BE-4.1
As a user, I want to submit my week so that my team lead can approve it.
- `draft | rejected → submitted`; sets approver via `approverFor`; notifies approver (TIMESHEET_SUBMITTED)

**FE-6.1 Weekly timesheet grid** · `frontend` · L · depends on T-6.1
As a user, I want a week grid so that entering hours is fast.
- Week navigation (prev/next/today), status badge
- Add project row, hour inputs per day, row and day totals, description per entry
- Approved absence days shown in the grid header
- Save button with unsaved-changes warning; read-only when submitted/approved; rejection comment shown when rejected

**FE-6.2 Submit timesheet** · `frontend` · S · depends on FE-6.1
- Submit button with confirmation; disabled with unsaved changes

**FE-6.3 Integrate timesheets** · `frontend` · S · depends on BE-6.2–6.4, FE-6.1, FE-6.2

---

### Epic 7 — Timesheets: approval

**BE-7.1 Team timesheets to approve** · `backend` · S · depends on BE-6.4
- `GET /api/team/timesheets?status=submitted` with user, week, total hours, hours per project

**BE-7.2 Approve or reject timesheet** · `backend` · S · depends on BE-7.1
- Same rules as BE-5.2; notifies the user (TIMESHEET_DECIDED)

**FE-7.1 Timesheets tab on Approvals page** · `frontend` · M · depends on FE-5.1
- Second tab on Approvals; expandable row shows the week grid read-only; approve/reject with comment

**FE-7.2 Integrate timesheet approval** · `frontend` · S · depends on BE-7.2, FE-7.1

---

### Epic 8 — Timesheet export

**T-8.1 Contract and templates** · `together` · M
Agree the export dialog, the list of templates (one per client + generic), and what each template contains. Gather real example sheets from each client if available.
Open question: **export only approved weeks, or everything?** (Proposal: everything, with a warning in the dialog if some weeks aren't approved.)

**BE-8.1 Template registry** · `backend` · S
- `GET /api/export-templates` returns `{ code, name, client }`; templates implemented as Spring beans behind one interface (strategy pattern) (D8)

**BE-8.2 Generate monthly Excel export** · `backend` · L · depends on BE-8.1, BE-6.3
As a user, I want to download my month as an Excel file in my client's format so that I can hand it in.
- `GET /api/me/timesheet-exports?month=YYYY-MM&template=` returns `.xlsx` (`Content-Disposition: attachment`)
- Includes all entries with `work_date` in the month (D7); absences listed where the template needs them
- Apache POI opens the template `.xlsx` and fills it (D8); start with the generic template, one story per client template afterwards if they differ a lot
- Test opens the generated file and checks key cells

**FE-8.1 Export dialog and download** · `frontend` · M · depends on T-8.1
As a user, I want an Export button per month so that I can pick a template and download the file.
- Month picker, template select (preselect template matching the user's client), download via blob from `client.ts`
- Warning if the month has non-approved weeks

**FE-8.2 Integrate export** · `frontend` · S · depends on BE-8.2, FE-8.1

---

### Epic 9 — Administration *(after the core features)*

**T-9.1 Contract: admin endpoints** · `together` · S

**BE-9.1 / FE-9.1 Manage users** · M each
As an admin, I want to create and edit users, set their team lead and admin flag.
- Can't create cycles in `team_lead_id`; email unique (409); initial password set by admin

**BE-9.2 / FE-9.2 Manage entitlements** · M each
As an admin, I want to set yearly entitlements and carry-over so that balances are right.
- Stretch: "start new year" action that computes carry-over from remaining days

**BE-9.3 / FE-9.3 Manage projects** · S each
As an admin, I want to create, edit and deactivate projects.

**BE-9.4 / FE-9.4 Manage public holidays** · S each
As an admin, I want to maintain the holiday list for next year.

---

### Stretch ideas (not planned yet)
- Live notifications with Server-Sent Events (replacing polling)
- Change own password
- JWT authentication as a learning comparison to sessions
- Expense sheets, if nobody else picks them up

---

## 7. Milestones

| Milestone | Epics | Demo |
|---|---|---|
| **M0 Foundation** | 0 | Stack runs locally, CI green, "hello" endpoint called from the FE through `client.ts` |
| **M1 Login** | 1 | Log in as different seeded users, see role-dependent nav |
| **M2 Absences end-to-end** | 2, 3, 4, 5 | User books vacation → team lead notified → approves → user notified, balance updates |
| **M3 Timesheets** | 6, 7 | User fills a week, submits, team lead approves |
| **M4 Export** | 8 | Download a month in the generic and one client template |
| **M5 Admin** | 9 | Admin sets up a new user with entitlements and team lead |

After each milestone: demo, retro, add entries to `learnings.md`.

---

## 8. Suggested fixes to CLAUDE.md

- "Three developers" lists only two roles (BE, FE). Fix the count or add the third role.
- Absence status flow says `withdrawn`, data model says `cancelled` → use `cancelled` (D2).
- Remove `seats` from the data model (D9) and add `public_holidays` (D4).
- Add `decision_comment` to `absence_requests` and `timesheets`.
- Mention Problem Details as the error format (D10) under API conventions.
- Replace the Flyway convention with Liquibase: "Every schema change needs a Liquibase changeset (src/main/resources/db/changelog, no ddl-auto=update)" (D12).
