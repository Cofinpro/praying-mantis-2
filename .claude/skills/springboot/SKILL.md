---
name: springboot
description: Conventions for the Spring Boot 4 / Java 21 / Maven backend in backend/. Use when adding or changing REST endpoints, services, configuration, validation, error handling or backend tests.
---

# Spring Boot backend (backend/)

Stack: Spring Boot 4.1 (Spring Framework 7, Jackson 3), Java 21, Maven via the bundled wrapper. Base package: `pt.cofinpro.prayingmantis`.

## Boot 4 gotchas

Boot 4 is modular — each technology has its own starter and its own test starter. Don't copy Boot 2/3 snippets blindly:

- Web is `spring-boot-starter-webmvc`; slice tests need `spring-boot-starter-webmvc-test`.
- `@WebMvcTest` lives in `org.springframework.boot.webmvc.test.autoconfigure`.
- Mock beans with `@MockitoBean` / `@MockitoSpyBean` (`org.springframework.test.context.bean.override.mockito`); `@MockBean` is gone.
- Jackson 3 packages are `tools.jackson.*` (annotations stay in `com.fasterxml.jackson.annotation`).
- Adding a feature (JPA, Flyway, security…) usually means adding both `spring-boot-starter-xxx` and, for its test slice, `spring-boot-starter-xxx-test`.

## Layout — package by feature

```
pt/cofinpro/prayingmantis/
├── PrayingMantisApplication.java
├── hello/                 existing sample feature
└── <feature>/
    ├── XxxController.java     @RestController, @RequestMapping("/api/<feature>")
    ├── XxxService.java        business logic, @Transactional boundaries
    ├── XxxRepository.java     Spring Data interface (see postgres skill)
    ├── Xxx.java               JPA entity
    └── XxxDto / records       request/response records
```

## Conventions

- All HTTP endpoints under `/api/...` (the Vite dev proxy forwards only `/api`).
- Request/response bodies are Java `record`s — never expose JPA entities directly from controllers.
- Constructor injection only (single constructor, no `@Autowired` on fields); make fields `final`.
- Validate input with Jakarta Validation: `@Valid @RequestBody CreateXxxRequest req` and constraints (`@NotBlank`, `@Size`, …) on record components.
- Errors: throw domain exceptions and map them in one `@RestControllerAdvice` returning `ProblemDetail` (RFC 9457). Use `ResponseEntity` only when status/headers vary; otherwise return the body and use `@ResponseStatus` (e.g. `CREATED` on POST).
- Configuration in `application.yml`; bind groups of properties with `@ConfigurationProperties` records instead of scattered `@Value`.
- Keep Actuator exposure to `health,info` unless asked otherwise.

## Tests

- Controllers: `@WebMvcTest(XxxController.class)` + `MockMvc` + `@MockitoBean` for the service (see `hello/HelloControllerTest.java` for style — static imports, `jsonPath`).
- Services: plain JUnit 5 + Mockito, no Spring context.
- Persistence / full-stack: see the `postgres` skill (Testcontainers).
- Test names describe behaviour: `returnsNotFoundForUnknownId()`.

## Commands (run in backend/)

On Windows PowerShell use `.\mvnw.cmd`; in bash use `./mvnw`.

```bash
./mvnw spring-boot:run          # http://localhost:8080
./mvnw test
./mvnw test -Dtest=HelloControllerTest
./mvnw verify                   # full build
```

Before declaring backend work done, run `./mvnw test`.
