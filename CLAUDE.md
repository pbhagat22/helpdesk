# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture

The code is organized by feature package under `com.example.helpdesk`: `user`, `ticket` (tickets and comments), `security` and `common` (exceptions and the global error handler). Each feature is layered Controller → Service → Repository:

- **Controllers** only handle HTTP. They take and return DTO records (`Create*Request`, `Update*Request`, `*Response`) and never expose JPA entities. Request bodies are checked with `@Valid` against Bean Validation annotations on the request records.
- **Services** own the transactions (`@Transactional(readOnly = true)` at class level, overridden on write methods) and convert entities to DTOs *inside* the transaction. `spring.jpa.open-in-view=false` is set, so lazy relationships read outside a service method throw `LazyInitializationException` instead of running hidden queries.
- **Repositories** are Spring Data interfaces. `TicketRepository.search` is one JPQL `@Query` where a null parameter means "no filter on this". Use that pattern rather than adding a derived method per filter combination. List queries use `@EntityGraph` to fetch `reporter`, `assignee` or `author` in the same query, which avoids N+1 queries. Keep this when adding list queries.
- **Entity relationships** are `@ManyToOne(fetch = LAZY)` and one-directional. Comment → Ticket has no collection on `Ticket`; comments are loaded with `CommentRepository`.
- **Errors:** services throw `NotFoundException` (404), `ConflictException` (409) or `BadRequestException` (400, for business rules) from `common`. `GlobalExceptionHandler` maps them, plus validation and Spring MVC errors, to RFC 9457 `ProblemDetail` JSON. Validation failures add an `errors` field→message map. Don't use `@ResponseStatus` on exceptions or return error bodies from controllers.
- **Security** (`security` package): stateless HTTP Basic with CSRF off and no session. Users log in with email and password, and `users.password_hash` stores a `{bcrypt}` hash from the delegating `PasswordEncoder`. `HelpdeskUserDetails` is the principal and carries the user's id and role. Controllers take `@AuthenticationPrincipal HelpdeskUserDetails me` and pass `me.getId()` to services, so who is acting (reporter, author) always comes from the login, never from the request body. `SecurityConfig` makes `POST /api/auth/register` (self sign-up as CUSTOMER), `/actuator/health/**` and `/error` public, and everything else requires authentication.
- **Authorization** happens in two places:
  - *Role rules* use `@PreAuthorize("hasAnyRole(...)")` on controller methods (`@EnableMethodSecurity`). Creating users is ADMIN only; listing or getting users and `PATCH /api/tickets/{id}` are AGENT or ADMIN.
  - *Ownership rules* depend on data, so they live in services, which receive the `HelpdeskUserDetails`. Customers only see and comment on tickets they reported. Someone else's ticket gives **404, not 403**, so its existence isn't revealed. `TicketService.list` forces `reporterId` to the customer's own id.
- **Security errors** (401/403) go through `SecurityProblemHandler`, which hands them to MVC's `handlerExceptionResolver`, so `GlobalExceptionHandler` renders them as ProblemDetail like everything else. It needs explicit `AuthenticationException` and `AccessDeniedException` handlers; without them the catch-all `Exception` handler would turn a `@PreAuthorize` denial into a 500.
- **Bootstrap admin:** `AdminBootstrap` (an `ApplicationRunner`) creates an ADMIN at startup from `helpdesk.admin.email` and `helpdesk.admin.password` (`AdminProperties`, set with the `HELPDESK_ADMIN_EMAIL` and `HELPDESK_ADMIN_PASSWORD` environment variables) if that email doesn't exist. `compose.yaml` sets dev values for the `app` service. With `spring-boot:run`, pass them as environment variables. Real credentials never go in committed files.

**Tests**, in `src/test/java`, mirror the main packages:
- `*ServiceTests`: plain Mockito unit tests with no Spring context.
- `*ControllerTests`: `@WebMvcTest` with a `@MockitoBean` service and `MockMvcTester` (AssertJ style). They `@Import(SecurityConfig.class)` so the real rules apply (a slice test doesn't pick up `@Configuration` classes on its own), and run requests as a user with `.with(user(new HelpdeskUserDetails(...)))`.
- `*RepositoryTests`: `@DataJpaTest` with `TestEntityManager`, against Testcontainers Postgres.
- `HelpdeskFlowTests`: end-to-end `@SpringBootTest` + `@AutoConfigureMockMvc` tests with nothing mocked. Users register and log in for real with `.with(httpBasic(email, password))`.

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
HELPDESK_ADMIN_EMAIL=admin@example.com HELPDESK_ADMIN_PASSWORD=admin-password ./mvnw spring-boot:run  # same, with a bootstrap admin
./mvnw test                            # run all tests
./mvnw test -Dtest=TicketControllerTests              # run a single test class
./mvnw test -Dtest=TicketServiceTests#updateAssignsTicketToAgent  # run a single test method
./mvnw clean package                   # build the jar into target/
docker compose --profile app up -d --build   # build the image and run app + Postgres in containers
docker compose --profile app down            # stop both (plain `down` leaves the app container running)
```

The `app` Compose service uses a profile, so plain `docker compose up -d` starts only Postgres for `spring-boot:run` development. Set `APP_PORT=18080` to publish the containerized app on another host port.

The [Dockerfile](Dockerfile) is multi-stage. A JDK stage runs `./mvnw package -DskipTests` (tests need Docker, which isn't available inside a build) with a BuildKit cache mount for `~/.m2`, then extracts Boot's jar layers (`-Djarmode=tools extract --layers --launcher`). The JRE stage copies the layers in order, from least to most frequently changing, runs as the non-root `spring` user, and starts with `JarLauncher`. Inside Compose the app reaches the database through `DB_URL=jdbc:postgresql://postgres:5432/helpdesk` (the service name, not `localhost`).

No linter or formatter is configured. Tests need Docker running (OrbStack is installed), but not `docker compose`. Testcontainers starts its own Postgres.

## Configuration

Config lives in `src/main/resources/application.properties`. The app connects to Postgres 18 from `compose.yaml` (database, user and password are all `helpdesk`). Override the connection with the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` environment variables. The SQL Hibernate runs is logged at debug level.

**Schema changes go through Flyway** (`src/main/resources/db/migration`, Postgres SQL). Hibernate runs with `ddl-auto=validate`, so it never changes tables, and startup fails if an entity doesn't match the schema. To change an entity's columns, add a new `V<n>__description.sql` migration alongside the entity change. Never edit a migration that has already been applied. Enum columns are `varchar(20)` with `@Column(length = 20)` and no check constraints. Foreign key columns used in queries get an explicit index, because Postgres doesn't index them automatically. To query the database directly: `docker compose exec postgres psql -U helpdesk -d helpdesk`.

Tests that need a database `@Import(TestcontainersConfiguration.class)`. Its `@ServiceConnection` Postgres container overrides `spring.datasource.*`, so tests never touch the Compose database. `@WebMvcTest` and plain unit tests don't need it.

When starting the app to test changes, check whether port 8080 is already in use (the user may have their own instance running). Use a different port, e.g. `-Dspring-boot.run.arguments=--server.port=18080`, and stop only the process you started.
