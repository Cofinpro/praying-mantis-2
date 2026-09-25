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

Then open http://localhost:5173. You land on the login page: sign in as one of the [dev users](#dev-users) (password `password`). The nav depends on the role: Approvals for team leads, Admin for admins.

No Docker Desktop? [Colima](https://github.com/abiosoft/colima) gives you the same `docker` and `docker compose` commands, and Testcontainers works with it too:

```bash
brew install colima docker docker-compose
# once: let the docker CLI find the compose plugin
echo '{ "cliPluginsExtraDirs": ["/opt/homebrew/lib/docker/cli-plugins"] }' > ~/.docker/config.json
colima start --cpu 2 --memory 4          # after each reboot
```

`pnpm` not found? Node ships Corepack, which runs the version pinned in `frontend/package.json`: run `corepack enable` once, or prefix commands with `corepack` (`corepack pnpm dev`).

## Database

Postgres 17 runs in Docker on `localhost:5432`. Database, user and password are all `prayingmantis` (local only).

```bash
docker compose up -d --wait                                        # start
docker compose ps                                                  # status
docker compose exec postgres psql -U prayingmantis prayingmantis   # SQL shell
docker compose down                                                # stop, keep data
docker compose down -v                                             # stop and wipe all data
```

### Dev users

`./mvnw spring-boot:run` starts the backend with the `dev` profile, which makes Liquibase load these seed users. Every password is `password`. If you start the app from your IDE instead, activate the `dev` profile there. Tests load the same seed through `src/test/resources/config/application.yml`.

| Email | Role | Team lead |
|---|---|---|
| `alex.admin@cofinpro.pt` | admin | – |
| `ana.silva@cofinpro.pt` | team lead of Bruno, Carla, Diogo | – |
| `bruno.costa@cofinpro.pt` | team lead of Eva, Filipe, Hugo | Ana |
| `carla.mendes@cofinpro.pt`, `diogo.pereira@cofinpro.pt` | employee | Ana |
| `eva.santos@cofinpro.pt`, `filipe.rocha@cofinpro.pt`, `hugo.marques@cofinpro.pt` | employee | Bruno |
| `gabriela.lopes@cofinpro.pt` | employee without team lead (her approvals go to the admins, decision #16) | – |

Every seed user has 22 vacation days for the year the database was created, and Ana and Carla carry days over. Carla has one absence request per status in that year: 5.5 approved vacation days, 3 pending, 2 rejected and 1 sick day, so her balance shows 19 days left.

Without the `dev` profile (e.g. the packaged jar), no seed users, entitlements or requests are created. The five absence types and the public holidays for 2026 and 2027 are reference data and are always loaded.

If port 5432 is taken (e.g. by a locally installed Postgres), stop that one first.

The backend connects to it by default. Override with the `DB_URL`, `DB_USER` and `DB_PASSWORD` env vars. Liquibase applies the schema on start (decision #23).

## Backend

```bash
cd backend
./mvnw spring-boot:run    # http://localhost:8080, needs the database running
./mvnw test               # needs Docker: tests start their own Postgres (Testcontainers)
```

Schema changes are Liquibase changesets in `src/main/resources/db/changelog/changes/`, one file per story, included from `db.changelog-master.yaml`.

Endpoints:
- `GET /actuator/health` – health check
- `GET /api/hello` – sample endpoint, public until login exists

The API contract is `api/openapi.yaml` (decision #2). On every build, `openapi-generator-maven-plugin` turns it into controller interfaces and DTOs in `target/generated-sources/openapi` (package `pt.cofinpro.prayingmantis.api`). Controllers implement those interfaces. To add an endpoint, change the contract, run `./mvnw compile`, and implement the new method: the build fails until you do. If your IDE doesn't see the generated classes, mark that folder as a generated sources root.

## Frontend

```bash
cd frontend
pnpm install
pnpm dev                # http://localhost:5173, /api goes to the backend
pnpm dev:mock           # same, but /api is served by the MSW mocks (no backend needed)
pnpm gen:api            # regenerate src/api/generated/ after api/openapi.yaml changes
pnpm lint               # ESLint (pnpm lint:fix to auto-fix)
pnpm format             # Prettier (pnpm format:check only reports)
pnpm test               # Vitest, single run (pnpm test:watch to watch)
pnpm type-check         # vue-tsc
pnpm build              # type-check + production build
```

In development, Vite proxies `/api/*` to `http://localhost:8080`, so no CORS setup is needed.

With `pnpm dev:mock` (decision #4), MSW answers the endpoints in `src/mocks/handlers.ts`. Other `/api` calls still go to the backend, with a console warning. Tests use the same handlers.

> pnpm only (decision #5). Never commit a `package-lock.json` or `yarn.lock`.

## CI

GitHub Actions runs `.github/workflows/backend.yml` on every PR and on `main`. The workflow runs `./mvnw verify`, which includes the Testcontainers tests. Because the API interfaces are regenerated from `api/openapi.yaml` in every build, a controller that doesn't match the contract fails the build. The `backend` check is required before merging (`scripts/setup-branch-protection.sh`).

`.github/workflows/frontend.yml` runs on the same triggers. In `frontend/`, it installs with the pnpm version pinned in `package.json` (`--frozen-lockfile`, so a `package.json` change without a lockfile update fails). Then it runs `pnpm gen:api` and fails if `src/api/generated/` changed, followed by `pnpm format:check`, `pnpm lint`, `pnpm test` and `pnpm build`. The `frontend` check is also required. To reproduce a failing run locally:

```bash
cd frontend
pnpm install --frozen-lockfile && pnpm gen:api && git diff --exit-code -- src/api/generated/ \
  && pnpm format:check && pnpm lint && pnpm test && pnpm build
```

## Contributing & code review

All changes go through a pull request. See [CONTRIBUTING.md](CONTRIBUTING.md) for the policy.

Every PR is reviewed by two AI mentors (`.claude/agents/`): `be-specialist` for `backend/**`, `fe-specialist` for `frontend/**`, and both for `api/**`. They flag problems and explain the Spring Boot / Vue concept behind each one. They run locally in Claude Code: type `/review-pr` to review your branch, or `/review-pr --post` to also post the review on the PR.

One-time repo setup (needs admin rights):

```bash
./scripts/setup-branch-protection.sh Cofinpro/praying-mantis-2
```
