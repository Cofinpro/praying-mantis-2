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

| Email | Password | Logs in as |
|---|---|---|
| any email, e.g. `ana.silva@cofinpro.pt` | `secret` | Ana Silva, team lead (not admin) |

Any other password gets the "Invalid email or password" error, so you can try the error state too. A page reload logs you out, because the mock keeps the session only in the open page. The password is `MOCK_PASSWORD` in `frontend/src/mocks/handlers.ts`; change both places together.
