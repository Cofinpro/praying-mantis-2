# Decisions

Architectural and tooling choices for praying-mantis-1, with the reasons behind them. Add new decisions at the bottom, and never delete one. If a decision changes, mark it **Superseded by #N** and add a new entry.

**Status values:** **Accepted** (agreed, follow it) · **Proposed** (from `plan.md`, to confirm in a `together` session) · **Superseded**

## Index

| # | Decision | Status |
|---|---|---|
| 1 | Tech stack | Accepted |
| 2 | Contract-first API with OpenAPI | Accepted |
| 3 | Generated code on both sides of the contract | Accepted |
| 4 | Frontend builds against MSW mocks | Accepted |
| 5 | pnpm for the frontend | Accepted |
| 6 | TanStack Query for server state, one API client module | Accepted |
| 7 | Spring Data JPA, business rules in services | Accepted |
| 8 | Enums stored as VARCHAR | Accepted |
| 9 | Derived values are computed, not stored | Accepted |
| 10 | Tests against a real Postgres with Testcontainers | Accepted |
| 11 | Backend enforces all permissions | Accepted |
| 12 | Session-based authentication with Spring Security | Proposed |
| 13 | Absence cancel status is `cancelled` | Proposed |
| 14 | No overlapping absences, enforced twice | Proposed |
| 15 | Working days exclude weekends and public holidays | Proposed |
| 16 | Approver fallback | Proposed |
| 17 | Notifications by polling | Proposed |
| 18 | Monthly export aggregates weekly timesheets | Proposed |
| 19 | Export templates as `.xlsx` files filled with Apache POI | Proposed |
| 20 | No seats table | Proposed |
| 21 | Errors as RFC 7807 Problem Details | Proposed |
| 22 | Date and time format | Proposed |
| 23 | Schema migrations with Liquibase | Accepted |
| 24 | The Figma file is the UI source of truth | Accepted |
| 25 | PRs merge without waiting for an approval | Accepted |
| 26 | Own month-calendar component, no calendar library | Proposed |
| 25 | Frontend lint, format and test tooling | Accepted |
| 26 | Frontend API client with openapi-fetch, committed generated types | Accepted |

`plan.md` decisions D1–D12 map to #12–#23.

---

## 1. Tech stack
**Status:** Accepted

**Decision:** The backend is Java with Spring Boot and Postgres. The frontend is Vue 3 and TypeScript on Vite.

**Why:** It's the company's target stack. A second goal of the project is learning it, so when there's a choice, we prefer the approach that teaches something over a shortcut that hides it.

## 2. Contract-first API with OpenAPI
**Status:** Accepted

**Decision:** `api/openapi.yaml` is the source of truth. For every feature, we agree the endpoints, JSON and error codes together (a `together` story) before either side implements them. All routes are REST + JSON under `/api`.

**Why:** Once the contract is merged, BE and FE can work in parallel and meet at a known interface.

**Consequences:** A contract change needs a PR that both devs review. *Changed by #25: both devs agree the contract in its `together` story; the PR itself no longer waits for an approval.*

## 3. Generated code on both sides of the contract
**Status:** Accepted

**Decision:**
- **Backend:** `openapi-generator-maven-plugin` generates Spring interfaces (interface-only). Controllers implement them.
- **Frontend:** `openapi-typescript` generates the API types (`pnpm gen:api`).

**Why:** The compiler catches drift from the contract on both sides.

**Consequences:** Generated code isn't edited by hand. CI fails if generated code is out of date.

## 4. Frontend builds against MSW mocks
**Status:** Accepted

**Decision:** Until the backend endpoint exists, the FE uses Mock Service Worker handlers that follow the contract. An env var switches between mocks and the real API. The same handlers are used in Vitest.

**Why:** FE work isn't blocked by BE, and the mocks double as test fixtures.

**Consequences:** Every feature ends with an integration story that switches from the mocks to the real API.

## 5. pnpm for the frontend
**Status:** Accepted

**Decision:** pnpm only, never npm or yarn. The lockfile is `pnpm-lock.yaml`.

## 6. TanStack Query for server state, one API client module
**Status:** Accepted

**Decision:**
- Server data goes through `@tanstack/vue-query`.
- `src/api/client.ts` is the only code that makes HTTP calls.
- Pages use Vue Router, and styles use `<style scoped>`.

**Why:** Query handles caching, refetching and invalidation, so we don't hand-roll a store for server data. A single client module handles 401s and error parsing in one place.

## 7. Spring Data JPA, business rules in services
**Status:** Accepted

**Decision:** DB access goes through Spring Data JPA repositories. Business rules live in `@Service` classes. `@RestController`s only map between the API and services.

**Why:** Rules can be tested without HTTP, and they're reused by several endpoints (e.g. the notification rules).

## 8. Enums stored as VARCHAR
**Status:** Accepted

**Decision:** Enums are stored as VARCHAR with `@Enumerated(EnumType.STRING)`, plus a DB `CHECK` constraint listing the allowed values.

**Why:** It's readable in SQL, and reordering the Java enum can't corrupt data, unlike `ORDINAL`.

## 9. Derived values are computed, not stored
**Status:** Accepted

**Decision:**
- *Is team lead* means "someone has me as `team_lead_id`".
- *Remaining days* = entitled + carried over − approved `working_days`.
- Neither is a column.
- The exception is `absence_requests.working_days`, which is computed once when the request is created and then stored, so a later holiday edit can't change a past request.

**Why:** A single source of truth, with no stale flags or balances.

## 10. Tests against a real Postgres with Testcontainers
**Status:** Accepted

**Decision:** Integration tests use JUnit 5 + `@SpringBootTest` against a Postgres started by Testcontainers. There's no H2. Pure logic (e.g. the working-days calculator) gets plain unit tests.

**Why:** We rely on Postgres-specific features (exclusion constraints, `daterange`) that H2 doesn't support.

**Consequences:** Docker is required locally and in CI.

## 11. Backend enforces all permissions
**Status:** Accepted

**Decision:** Every permission is checked in the backend. Hiding buttons in the UI is only a convenience.

**Why:** Anyone can call the API directly.

---

## 12. Session-based authentication with Spring Security
**Status:** Proposed · plan D1

**Decision:**
- `POST /api/auth/login` checks a BCrypt password hash and creates a server session. The session is kept in an HttpOnly cookie with `SameSite=Lax`.
- `POST /api/auth/logout` invalidates the session.
- The Vite dev server proxies `/api`, so FE and BE share an origin and no CORS setup is needed.
- CSRF protection stays on (Spring Security's SPA setup, `csrf.spa()`): the backend sets a readable `XSRF-TOKEN` cookie, and `client.ts` copies it into the `X-XSRF-TOKEN` header on every unsafe request, login and logout included. A missing or wrong token is a 403. Added in T-1.1.

**Alternatives considered:** JWT access and refresh tokens. That means more moving parts (storage, refresh, revocation), and there's no benefit for a same-origin internal app.

**Why:** It teaches the Spring Security filter chain without token plumbing. JWT stays a possible learning story later.

## 13. Absence cancel status is `cancelled`
**Status:** Proposed · plan D2

**Decision:** An absence can end in `pending`, `approved`, `rejected` or `cancelled`. The early notes also used `withdrawn`; that name is dropped.

**Why:** One name in the DB, the API and the UI.

## 14. No overlapping absences, enforced twice
**Status:** Proposed · plan D3

**Decision:**
- The service rejects an overlap with the user's own pending or approved requests with a 409.
- A Postgres exclusion constraint (`btree_gist`, `daterange(start_date, end_date, '[]')`, filtered on status) guarantees it at the DB level.

**Why:** The service gives a clear error message. The constraint also holds under concurrent requests, where a check-then-insert in the service could race.

## 15. Working days exclude weekends and public holidays
**Status:** Proposed · plan D4

**Decision:** New table `public_holidays(date, name)`, holding Portugal's national holidays. A request's working days leave out weekends and holidays. Morning or afternoon on the start or end day counts as 0.5: a multi-day request can start in the afternoon and end in the morning, and a single day can be a morning or an afternoon (T-2.1).

**Why:** Without holidays, balances are wrong around Christmas and Easter.

**Open:** Municipal holidays (e.g. Lisbon's 13 June) aren't in the list. If colleagues work in different cities or countries, one holiday list may not be enough. Revisit if needed.

## 16. Approver fallback
**Status:** Proposed · plan D5

**Decision:** The approver is the user's team lead. A user without a team lead has their requests go to the admins. A team lead's own requests go to *their* team lead. The same rule applies to timesheets.

**Refined in BE-1.3 (to confirm together, since #16 is Proposed):** `approver_id` holds one user, so "the admins" means one of them: the first admin by id who isn't the requester. Who may *decide*: the stored approver, or any admin, but **never the requester**, not even an admin (`Permissions.requireApproverOrAdmin`). The only user without an approver is the sole admin who has no team lead; creating their request is rejected with 409 until they get a team lead or a second admin exists (BE-3.2, BE-6.4). `Permissions.approverFor` implements the choice.

**Why:** No request or timesheet can get stuck without an approver.

## 17. Notifications by polling
**Status:** Proposed · plan D6

**Decision:**
- The notification bell calls `GET /api/me/notifications/unread-count` every 30 s, using TanStack Query `refetchInterval`.
- It also refetches when the browser tab regains focus.
- The server never pushes updates.

**Alternatives considered:** Server-Sent Events and WebSockets. Both deliver instantly but need extra infrastructure on both sides.

**Why:** It's plain REST, with nothing extra to build.

**Consequences:** A new notification can take up to 30 s to show up, and each open tab sends a tiny request every 30 s. That's acceptable for an internal tool. Server push stays a stretch story.

## 18. Monthly export aggregates weekly timesheets
**Status:** Proposed · plan D7

**Decision:** The export for a month contains all time entries whose `work_date` falls in that month, regardless of which weekly timesheet they belong to.

**Why:** Timesheets are weekly, but exports are monthly, and weeks cross month borders.

**Open:** Whether weeks that aren't approved yet are included. The proposal is to include them, with a warning in the export dialog.

## 19. Export templates as `.xlsx` files filled with Apache POI
**Status:** Proposed · plan D8

**Decision:**
- There is one template per client plus a generic one.
- Each template is the client's real `.xlsx` file, kept in `src/main/resources/export-templates/`.
- A Java class per template fills in its cells. All these classes implement one interface (strategy pattern), and `GET /api/export-templates` lists them.
- Apache POI, the standard Java library for reading and writing Office files, opens and fills the workbook.
- Templates are not stored in the DB.

**Why:** Templates change rarely and need code anyway. Starting from the client's own file keeps its formatting and logos for free. No admin UI is needed.

## 20. No seats table
**Status:** Proposed · plan D9

**Decision:** The `seats` table from the first sketch is removed from our schema.

**Why:** Seat reservation belongs to another group. We only show a nav link to it.

## 21. Errors as RFC 7807 Problem Details
**Status:** Proposed · plan D10

**Decision:**
- All error responses are `application/problem+json`, built with Spring's `ProblemDetail` in one `@RestControllerAdvice`.
- Validation errors list their fields in an `errors` extension.
- Status codes: 400 validation · 401 not logged in · 403 not allowed · 404 not found · 409 conflict (overlap, wrong status).

**Why:** It's a standard, built into Spring Boot 3, and gives the FE one error shape to handle in `client.ts`.

## 22. Date and time format
**Status:** Proposed · plan D11

**Decision:** Dates are ISO `YYYY-MM-DD` (`LocalDate`). Timestamps are ISO-8601 in UTC (`Instant`, `timestamptz`). A timesheet's `week_start` is always a Monday, enforced by a DB `CHECK` and validated by the API with a 400.

**Why:** Absences and time entries are calendar days, not moments, so storing them as dates avoids timezone off-by-one bugs.

## 23. Schema migrations with Liquibase
**Status:** Accepted · plan D12 · set up in BE-0.1

**Decision:**
- Every schema change is a Liquibase changeset. We never use `ddl-auto=update`; Hibernate runs with `ddl-auto=validate`.
- The master changelog is `src/main/resources/db/changelog/db.changelog-master.yaml`, and it includes one YAML changelog per story.
- Postgres-specific parts (exclusion constraints, `CHECK`s) use the `sql` change type.
- Dev seed data lives in changesets with `context: dev`.

**Alternatives considered:** Flyway, the tool in the original conventions, which uses plain versioned SQL files.

**Why:** It's the team's choice. Liquibase tracks each changeset by id, author and checksum, and supports rollbacks and contexts. YAML changesets teach the Liquibase model, while the `sql` change type keeps full Postgres power where we need it.

**Consequences:** A changeset that has already run is never edited, because its checksum would fail; fixes go in a new changeset. The convention in CLAUDE.md needs to be updated from Flyway to Liquibase.

## 24. The Figma file is the UI source of truth
**Status:** Accepted

**Decision:**
- Every FE story is built from its frame in the Figma file [Praying Mantis – Timesheets & Vacations](https://www.figma.com/design/7gmKCqksPty2qzbRnrWpJZ). `design.md` maps each story to its frame and lists the design tokens.
- A story with no frame, or a state the frame doesn't show, is designed in Figma first and agreed by the team. If the implementation has to differ, Figma is updated first.
- Components use the design tokens in `frontend/src/assets/tokens.css` (CSS custom properties), never raw colours, fonts or radii.
- Every FE PR links its frame and includes a screenshot.

**Alternatives considered:** Designing each screen in the `together` session on a whiteboard; a component library such as Vuetify or PrimeVue with its own look.

**Why:** One agreed picture per screen makes the `together` session faster and gives the FE dev and the reviewers something concrete to check against. The Cofinpro look (Inter, orange `#FD6202`, pill buttons) makes the platform feel like a company tool. Hand-writing our own components from tokens teaches more Vue and scoped CSS than theming a library.

**Consequences:** `design.md` must be kept in sync with Figma, in the same PR as the Figma change. The file has to be shared with the whole team with edit access. The `fe-specialist` reviewer checks FE PRs against `design.md`.
**Consequences:** A changeset that has already run is never edited, because its checksum would fail; fixes go in a new changeset. The Flyway convention in CLAUDE.md was replaced with Liquibase in BE-0.1.

## 25. Frontend lint, format and test tooling
**Status:** Accepted · set up in FE-0.1

**Decision:**
- ESLint 10 with a flat config (`eslint.config.ts`): `eslint-plugin-vue` essential rules, `@vue/eslint-config-typescript` recommended, and `@vitest/eslint-plugin` for test files.
- Prettier formats; `@vue/eslint-config-prettier/skip-formatting` turns off the ESLint rules that would conflict with it. Style: no semicolons, single quotes, 100 columns.
- Vitest with jsdom, reusing `vite.config.ts` through `vitest.config.ts`. Tests type-check against their own `tsconfig.vitest.json`.
- Scripts: `pnpm lint`, `pnpm format`, `pnpm test`, `pnpm type-check`, `pnpm build`. CI runs `pnpm format:check && pnpm lint && pnpm test && pnpm build` (`format:check` added in FE-0.3, since nothing else enforces formatting once `skip-formatting` turns the ESLint style rules off).

**Why:** It's the setup `create-vue` generates, so docs and examples online match ours. Vitest shares Vite's transform pipeline, so tests compile `.vue` files exactly like the app does.

## 26. Frontend API client with openapi-fetch, committed generated types
**Status:** Accepted · set up in FE-0.2

**Decision:**
- `pnpm gen:api` runs `openapi-typescript` on `api/openapi.yaml` and writes `src/api/generated/openapi.ts`. The file is committed, and ESLint and Prettier skip it.
- `src/api/client.ts` wraps `openapi-fetch`, a thin typed `fetch` from the same authors. It exposes one function per operation on an `api` object, and turns every non-2xx response into an `ApiError` holding a Problem Details body (built from the status when the body isn't one).
- On 401 the client navigates to the `login` route with `?redirect=<current path>`, except when already on it.
- MSW (decision #4): `pnpm dev:mock` (`vite --mode mock`, `frontend/.env.mock` sets `VITE_API_MOCKS=true`) starts the browser worker before mounting the app. Vitest starts the same handlers with `msw/node` in `src/test/setup.ts`. `public/mockServiceWorker.js` is generated by `msw init` and committed, and pnpm is allowed to run MSW's install script, which keeps that file in step with the MSW version.

**Alternatives considered:** A hand-written `fetch` wrapper typed with the generated `paths`. It's more code for the same checks. Generating a full client (e.g. `openapi-generator` typescript-fetch) hides too much of what happens on the wire.

**Why:** Paths, parameters and bodies are checked against the contract at compile time, with no runtime code generated. Committing the types means `pnpm install && pnpm dev` works without a generate step, and a contract change shows up in the PR diff.

**Consequences:** After changing `api/openapi.yaml`, run `pnpm gen:api` and commit the result. CI (`.github/workflows/frontend.yml`, FE-0.3) runs `pnpm gen:api` and fails if `src/api/generated/` changed.

## 25. PRs merge without waiting for an approval
**Status:** Accepted

**Decision:**
- Every change still goes through a PR with a green build and the checklist in `CONTRIBUTING.md`. But the author merges it without waiting for a code-owner approval.
- Human reviews are optional. Ask a code owner when you want one; a review can also happen after the merge, with fixes in a follow-up PR.
- Contract changes are still agreed by both devs, in their `together` story (#2).

**Alternatives considered:** 1 code-owner approval before merge (the original PR policy from T-0.1).

**Why:** The team chose not to block merges on an approval (2026-09-25). GitHub couldn't enforce the old rule on this private repo anyway: branch protection needs a paid plan (GitHub Pro/Team).

**Consequences:** Reviews happen by asking, not by default, so it's on each author to ask for one on risky changes. The AI mentor review (`/review-pr`) stays in the checklist.

## 26. Own month-calendar component, no calendar library
**Status:** Proposed · FE-2.2 ("decide together: own component vs library")

**Decision:** The absence calendar is our own Vue component (`AbsenceCalendar.vue`). A pure `absences/calendar.ts` builds the weeks, and dates are `YYYY-MM-DD` strings with UTC-based helpers in `format/dates.ts` (#22). No calendar library, and no date library either.

**Alternatives considered:** FullCalendar or v-calendar, and a date library such as date-fns or Day.js.

**Why:**
- We only need a month grid with coloured chips, half days, holidays and a pending style. The design is specific (Figma frame "02 Absences"), and a library would need as much custom rendering and CSS as the component itself.
- The team is here to learn Vue: computed data, reactive query keys and scoped CSS are the lesson.
- Calendar days as plain strings avoid the timezone bugs that `Date` objects bring (#22).

**Consequences:**
- We maintain about 300 lines of component and helpers ourselves, with unit tests for the week building.
- The team calendar (FE-5.3) can reuse `calendar.ts`.
- If we later need week views, drag-to-book or recurring events, revisit this.

