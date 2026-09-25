# praying-mantis-2

Monorepo with a Spring Boot backend and a Vue.js frontend.

```
.
├── backend/    Spring Boot 4 (Java 21, Maven)
└── frontend/   Vue 3 + Vite + TypeScript (Vue Router, Pinia, Vitest)
```

## Prerequisites

- JDK 21+
- Maven 3.9+ (or run `mvn wrapper:wrapper` once in `backend/` to add `./mvnw`)
- Node.js 22+ and npm

## Backend

```bash
cd backend
mvn spring-boot:run     # http://localhost:8080
mvn test
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
