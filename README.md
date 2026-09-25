# praying-mantis-2

Monorepo with a Spring Boot backend and a Vue.js frontend.

```
.
├── backend/             Spring Boot 4 (Java 21, Maven)
├── frontend/            Vue 3 + Vite + TypeScript (Vue Router, Pinia)
├── docker-compose.yml   Local Postgres
├── plan.md              Plan: scope, data model, user stories
├── decisions.md         Architectural and tooling decisions (#1, #2, …)
└── learnings.md         What we learned, by technology
```

## Prerequisites

- JDK 21+
- Maven is optional: use the bundled `./mvnw` wrapper
- Node.js 22+ and pnpm (run `corepack enable` once, after `brew install corepack` on Node 25+. Corepack picks the version pinned in `frontend/package.json`)
- Docker Desktop (or another Docker engine with Compose) for Postgres and, later, Testcontainers (decision #10)

## Run the whole stack

Each in its own terminal, from the repo root:

```bash
docker compose up -d --wait              # 1. database, waits until it's healthy
cd backend && ./mvnw spring-boot:run     # 2. backend  -> http://localhost:8080
cd frontend && pnpm install && pnpm dev   # 3. frontend -> http://localhost:5173
```

Then open http://localhost:5173.

## Database

Postgres 17 runs in Docker on `localhost:5432`. Database, user and password are all `prayingmantis` (local only).

```bash
docker compose up -d --wait                                        # start
docker compose ps                                                  # status
docker compose exec postgres psql -U prayingmantis prayingmantis   # SQL shell
docker compose down                                                # stop, keep data
docker compose down -v                                             # stop and wipe all data
```

If port 5432 is taken (e.g. by a locally installed Postgres), stop that one first.

> The backend still uses a file-based H2 database. BE-0.1 switches it to this Postgres with Liquibase (decision #23).

## Backend

```bash
cd backend
./mvnw spring-boot:run    # http://localhost:8080
```

Endpoints:
- `GET /actuator/health` – health check

## Frontend

```bash
cd frontend
pnpm install
pnpm dev                # http://localhost:5173
pnpm build
```

In development, Vite proxies `/api/*` to `http://localhost:8080`, so no CORS setup is needed.

> pnpm only (decision #5). Never commit a `package-lock.json` or `yarn.lock`.

## Contributing & code review

All changes go through a pull request. See [CONTRIBUTING.md](CONTRIBUTING.md) for the policy.

Every PR is reviewed by two AI mentors (`.claude/agents/`): `be-specialist` for `backend/**`, `fe-specialist` for `frontend/**`, and both for `api/**`. They flag problems and explain the Spring Boot / Vue concept behind each one. They run locally in Claude Code: type `/review-pr` to review your branch, or `/review-pr --post` to also post the review on the PR.

One-time repo setup (needs admin rights):

```bash
./scripts/setup-branch-protection.sh Cofinpro/praying-mantis-2
```
