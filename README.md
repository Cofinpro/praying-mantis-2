# praying-mantis-2

Monorepo with a Spring Boot backend and a Vue.js frontend.

```
.
├── backend/    Spring Boot 4 (Java 21, Maven)
└── frontend/   Vue 3 + Vite + TypeScript (Vue Router, Pinia)
```

## Prerequisites

- JDK 21+
- Maven is optional: use the bundled `./mvnw` wrapper
- Node.js 22+ and npm

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
npm install
npm run dev             # http://localhost:5173
npm run build
```

In development, Vite proxies `/api/*` to `http://localhost:8080`, so no CORS setup is needed.

## Contributing & code review

All changes go through a pull request. See [CONTRIBUTING.md](CONTRIBUTING.md) for the policy.

Every PR is reviewed by two AI mentors (`.claude/agents/`): `be-specialist` for `backend/**`, `fe-specialist` for `frontend/**`, and both for `api/**`. They flag problems and explain the Spring Boot / Vue concept behind each one. They run locally in Claude Code: type `/review-pr` to review your branch, or `/review-pr --post` to also post the review on the PR.

One-time repo setup (needs admin rights):

```bash
./scripts/setup-branch-protection.sh Cofinpro/praying-mantis-2
```
