# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture

The code is organized by feature package under `com.example.helpdesk`: `user`, `ticket` (tickets and comments) and `common` (exceptions and the global error handler). Each feature is layered Controller → Service → Repository:

- **Controllers** only handle HTTP. They take and return DTO records (`Create*Request`, `Update*Request`, `*Response`) and never expose JPA entities. Request bodies are checked with `@Valid` against Bean Validation annotations on the request records.
- **Services** own the transactions (`@Transactional(readOnly = true)` at class level, overridden on write methods) and convert entities to DTOs *inside* the transaction. `spring.jpa.open-in-view=false` is set, so lazy relationships read outside a service method throw `LazyInitializationException` instead of running hidden queries.
- **Repositories** are Spring Data interfaces using derived queries. Ticket and comment list queries use `@EntityGraph` to fetch `reporter`, `assignee` or `author` in the same query, which avoids N+1 queries. Keep this when adding list queries.
- **Entity relationships** are `@ManyToOne(fetch = LAZY)` and one-directional. Comment → Ticket has no collection on `Ticket`; comments are loaded with `CommentRepository`.
- **Errors:** services throw `NotFoundException` (404), `ConflictException` (409) or `BadRequestException` (400, for business rules) from `common`. `GlobalExceptionHandler` maps them, plus validation and Spring MVC errors, to RFC 9457 `ProblemDetail` JSON. Validation failures add an `errors` field→message map. Don't use `@ResponseStatus` on exceptions or return error bodies from controllers.
- **No authentication yet:** `reporterId` and `authorId` come from the request body until Spring Security is added (roadmap stage 4).

**Tests**, in `src/test/java`, mirror the main packages:
- `*ServiceTests`: plain Mockito unit tests with no Spring context.
- `*ControllerTests`: `@WebMvcTest` with a `@MockitoBean` service and `MockMvcTester` (AssertJ style).
- `*RepositoryTests`: `@DataJpaTest` with `TestEntityManager`, against Testcontainers Postgres.
- `HelpdeskFlowTests`: one end-to-end `@SpringBootTest` + `@AutoConfigureMockMvc` test with nothing mocked.

In Spring Boot 4 the test annotations live in per-module packages, e.g. `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` and `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`.

## Roadmap

This is a learning project: a support helpdesk (Users, Tickets, Comments, later KB articles) built up in stages, one new skill per stage. Check `git log` to see which stage is done before continuing.

1. REST API + Git
2. Postgres via Docker Compose, Flyway migrations, Testcontainers
3. Dockerfile
4. Spring Security
5. React UI
6. GitHub Actions CI (= version 1)
   - 6b. Kafka events
   - 6c. Elasticsearch search
7. AWS by hand (ECS Fargate, RDS; cost guardrails first)
8. Terraform + OIDC-based CD
9. AI features
   - 9a. Bedrock LLM basics
   - 9b. Agent with tools
   - 9c. RAG with pgvector
   - 9d. MCP server/client
10. Kubernetes (kind locally, then short EKS sessions)

`sandbox/spring_demo/demo` (a Todo app) is a separate scratch sandbox, not part of this project.

## Stack

- **Spring Boot 4.1.1**, **Java 25**, Maven (use the bundled `./mvnw` wrapper).
- Dependencies: Spring Web MVC (`spring-boot-starter-webmvc`), Spring Data JPA, Bean Validation, Actuator, DevTools, the PostgreSQL driver, and Flyway (`spring-boot-starter-flyway` plus `flyway-database-postgresql`, which Flyway needs for Postgres support). Tests use Testcontainers 2.x (`testcontainers-postgresql`, class `org.testcontainers.postgresql.PostgreSQLContainer`; the 1.x package names don't apply).
- Spring Boot 4 splits starters into finer modules, and each has its own test starter (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, etc.). When you add a starter, also add its matching `-test` starter if tests need its slices (`@WebMvcTest`, `@DataJpaTest`). Spring Boot 2/3 starter names (e.g. `spring-boot-starter-web`) don't apply here.
- `pom.xml` contains empty `<licenses>`, `<developers>`, and `<scm>` elements on purpose. They block inheritance from the parent POM, so don't remove them.

## Commands

```bash
docker compose up -d                   # start Postgres (required before running the app)
docker compose down                    # stop it (data kept in the pgdata volume; add -v to wipe)
./mvnw spring-boot:run                 # run the app (port 8080 by default)
./mvnw test                            # run all tests
./mvnw test -Dtest=TicketControllerTests              # run a single test class
./mvnw test -Dtest=TicketServiceTests#updateAssignsTicketToAgent  # run a single test method
./mvnw clean package                   # build the jar into target/
./mvnw spring-boot:build-image         # build an OCI image
```

No linter or formatter is configured. Tests need Docker running (OrbStack is installed), but not `docker compose`. Testcontainers starts its own Postgres.

## Configuration

Config lives in `src/main/resources/application.properties`. The app connects to Postgres 18 from `compose.yaml` (database, user and password are all `helpdesk`). Override the connection with the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` environment variables. The SQL Hibernate runs is logged at debug level.

**Schema changes go through Flyway** (`src/main/resources/db/migration`, Postgres SQL). Hibernate runs with `ddl-auto=validate`, so it never changes tables, and startup fails if an entity doesn't match the schema. To change an entity's columns, add a new `V<n>__description.sql` migration alongside the entity change. Never edit a migration that has already been applied. Enum columns are `varchar(20)` with `@Column(length = 20)` and no check constraints. Foreign key columns used in queries get an explicit index, because Postgres doesn't index them automatically. To query the database directly: `docker compose exec postgres psql -U helpdesk -d helpdesk`.

Tests that need a database `@Import(TestcontainersConfiguration.class)`. Its `@ServiceConnection` Postgres container overrides `spring.datasource.*`, so tests never touch the Compose database. `@WebMvcTest` and plain unit tests don't need it.

When starting the app to test changes, check whether port 8080 is already in use (the user may have their own instance running). Use a different port, e.g. `-Dspring-boot.run.arguments=--server.port=18080`, and stop only the process you started.
