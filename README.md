# praying-mantis-2

Monorepo with a Spring Boot backend and a Vue.js frontend.

```
.
├── backend/    Spring Boot 4 (Java 21, Maven)
└── frontend/   Vue 3 + Vite + TypeScript (Vue Router, Pinia, Vitest)
```

## Prerequisites

- JDK 21+
- Maven is optional: use the bundled `./mvnw` wrapper
- Node.js 22+ and npm

## Backend

```bash
cd backend
./mvnw spring-boot:run    # http://localhost:8080
./mvnw test
```

Endpoints:
- `GET /api/hello?name=...` – sample endpoint
- `GET /actuator/health` – health check

## Frontend

```bash
cd frontend
npm install
npm run dev             # http://localhost:5173
npm run test:unit
npm run build
```

In development, Vite proxies `/api/*` to `http://localhost:8080`, so no CORS setup is needed.
