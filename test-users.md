# Test users

Logins for local development, tests and the demos. These are **mock credentials only**. They're public on purpose, so never reuse them for anything real, and never load the dev seed into a database with real data.

## Backend (local, tests, Render)

Liquibase loads these users with the `dev` context: `./mvnw spring-boot:run` locally, the test config, and the Render deployment (decision #30). Log in at http://localhost:5173 with `pnpm dev`, or against the Render backend.

| Email | Password | Role | Team lead |
|---|---|---|---|
| `alex.admin@cofinpro.pt` | `password` | admin, the only one | – |
| `ana.silva@cofinpro.pt` | `password` | team lead of Bruno, Carla and Diogo | – |
| `bruno.costa@cofinpro.pt` | `password` | team lead of Eva, Filipe and Hugo | Ana |
| `carla.mendes@cofinpro.pt` | `password` | employee, with one absence request per status (19 vacation days left, 3 pending) | Ana |
| `diogo.pereira@cofinpro.pt` | `password` | employee | Ana |
| `eva.santos@cofinpro.pt` | `password` | employee | Bruno |
| `filipe.rocha@cofinpro.pt` | `password` | employee | Bruno |
| `hugo.marques@cofinpro.pt` | `password` | employee | Bruno |
| `gabriela.lopes@cofinpro.pt` | `password` | employee without a team lead; her approvals go to the admin (decision #16) | – |

Every one of them has 22 vacation days for the year the database was created. Ana and Carla also carry days over.

## Frontend demo (GitHub Pages, `pnpm dev:mock`)

The demo at https://cofinpro.github.io/praying-mantis-2/ and `pnpm dev:mock` run on the MSW mocks, not the backend (decision #4).

The mock knows **the same people as the backend** (table above), with the same password `password`, so a login works the same on both. Each person gets their own name, level, client and role (the nav shows Approvals for Ana and Bruno, Admin for Alex). An email that isn't in the table gets "Invalid email or password", like the real API.

The rest of the mock data (balance, absences, notifications, …) is one demo data set that every user sees; only the real backend has per-person data.

A wrong password gets the same error, so you can try the error state too. A page reload logs you out, because the mock keeps the session only in the open page. The users are in `frontend/src/mocks/data/users.ts` and the password is `MOCK_PASSWORD` in `frontend/src/mocks/handlers.ts`; keep them in step with the backend seed and this file.
