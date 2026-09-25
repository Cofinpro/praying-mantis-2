---
name: postgres
description: How to add and work with PostgreSQL in the backend — Spring Data JPA, Flyway migrations, local Docker database and Testcontainers tests. Use when adding persistence, entities, repositories, schema changes or SQL queries.
---

# PostgreSQL persistence

The backend has **no database yet**. When persistence is first needed, set it up as below; afterwards follow the conventions section.

## First-time setup

1. Dependencies in `backend/pom.xml` (versions come from the Boot parent):

   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-data-jpa</artifactId>
   </dependency>
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-flyway</artifactId>
   </dependency>
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-database-postgresql</artifactId>
   </dependency>
   <dependency>
       <groupId>org.postgresql</groupId>
       <artifactId>postgresql</artifactId>
       <scope>runtime</scope>
   </dependency>

   <!-- tests -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-data-jpa-test</artifactId>
       <scope>test</scope>
   </dependency>
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-testcontainers</artifactId>
       <scope>test</scope>
   </dependency>
   <dependency>
       <groupId>org.testcontainers</groupId>
       <artifactId>testcontainers-postgresql</artifactId>
       <scope>test</scope>
   </dependency>
   ```

   If an artifact doesn't resolve, check the managed versions in the Boot BOM rather than hard-coding versions (Testcontainers 1.x used `org.testcontainers:postgresql`).

2. Local database — add `compose.yaml` at the repo root:

   ```yaml
   services:
     postgres:
       image: postgres:17
       environment:
         POSTGRES_DB: prayingmantis
         POSTGRES_USER: prayingmantis
         POSTGRES_PASSWORD: prayingmantis
       ports:
         - "5432:5432"
       volumes:
         - pgdata:/var/lib/postgresql/data
   volumes:
     pgdata:
   ```

3. `application.yml`:

   ```yaml
   spring:
     datasource:
       url: ${DB_URL:jdbc:postgresql://localhost:5432/prayingmantis}
       username: ${DB_USER:prayingmantis}
       password: ${DB_PASSWORD:prayingmantis}
     jpa:
       open-in-view: false
       hibernate:
         ddl-auto: validate
   ```

4. Update README prerequisites (Docker) and the run instructions (`docker compose up -d`).

## Conventions

- **Schema is owned by Flyway.** Never use `ddl-auto: create/update`. Every change is a new file in `backend/src/main/resources/db/migration/` named `V<n>__<snake_case_description>.sql` (two underscores). Never edit a migration that has been committed — add a new one.
- SQL style: lowercase snake_case tables (plural, e.g. `mantises`) and columns; `id bigint generated always as identity primary key`; `timestamptz` for timestamps; `text` over `varchar(n)` unless a length limit is a real rule; explicit `not null`; name constraints/indexes (`fk_<table>_<ref>`, `idx_<table>_<cols>`); index foreign-key columns.
- Entities: `@Entity` + `@Table(name = "...")`, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`, `Long` ids, protected no-arg constructor, `@Column` names matching the migration. Associations default to `FetchType.LAZY`; avoid bidirectional mappings unless needed. Use `Instant` for `timestamptz`.
- Repositories: `interface XxxRepository extends JpaRepository<Xxx, Long>` in the feature package. Prefer derived queries; use `@Query` (JPQL) for anything complex, native SQL only when Postgres-specific features are needed.
- Transactions on service methods (`@Transactional`, `readOnly = true` for reads), not on controllers or repositories. Map entities to DTO records inside the transaction.
- Watch for N+1: use `JOIN FETCH` or `@EntityGraph` when a list view needs associations.

## Tests (need Docker running)

Shared Testcontainers config in `src/test/java/pt/cofinpro/prayingmantis/TestcontainersConfiguration.java`:

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17");
    }
}
```

- Repository tests: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = Replace.NONE)` + `@Import(TestcontainersConfiguration.class)` so they run against real Postgres with Flyway migrations applied.
- `@SpringBootTest` integration tests (including `PrayingMantisApplicationTests`) must also `@Import(TestcontainersConfiguration.class)` once a datasource exists, otherwise the context fails to start.
- Don't use H2 as a stand-in for Postgres.

## Useful commands

```bash
docker compose up -d                       # start local db
docker compose exec postgres psql -U prayingmantis prayingmantis
docker compose down                        # stop (add -v to wipe data)
```
