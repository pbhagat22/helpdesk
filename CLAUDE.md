# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture

The code is organized by feature package under `com.example.helpdesk`: `user`, `ticket` (tickets, comments, and publishing ticket events), `activity` (the Kafka consumer that builds ticket history), `security` and `common` (exceptions and the global error handler). Each feature is layered Controller → Service → Repository:

- **Controllers** only handle HTTP. They take and return DTO records (`Create*Request`, `Update*Request`, `*Response`) and never expose JPA entities. Request bodies are checked with `@Valid` against Bean Validation annotations on the request records.
- **Services** own the transactions (`@Transactional(readOnly = true)` at class level, overridden on write methods) and convert entities to DTOs *inside* the transaction. `spring.jpa.open-in-view=false` is set, so lazy relationships read outside a service method throw `LazyInitializationException` instead of running hidden queries.
- **Repositories** are Spring Data interfaces. `TicketRepository.search` is one JPQL `@Query` where a null parameter means "no filter on this". Use that pattern rather than adding a derived method per filter combination. List queries use `@EntityGraph` to fetch `reporter`, `assignee` or `author` in the same query, which avoids N+1 queries. Keep this when adding list queries.
- **Entity relationships** are `@ManyToOne(fetch = LAZY)` and one-directional. Comment → Ticket has no collection on `Ticket`; comments are loaded with `CommentRepository`.
- **Errors:** services throw `NotFoundException` (404), `ConflictException` (409) or `BadRequestException` (400, for business rules) from `common`. `GlobalExceptionHandler` maps them, plus validation and Spring MVC errors, to RFC 9457 `ProblemDetail` JSON. Validation failures add an `errors` field→message map. Don't use `@ResponseStatus` on exceptions or return error bodies from controllers.
- **Security** (`security` package) uses stateless JWT bearer tokens with CSRF off and no session. HTTP Basic is not enabled.
  - `POST /api/auth/login` (`TokenService`) checks email and password once, using a `DaoAuthenticationProvider` over `HelpdeskUserDetailsService` and the `{bcrypt}` hash in `users.password_hash`. It returns an HS256 JWT signed by Spring's `JwtEncoder`. Claims: `iss=helpdesk`, `sub=<user id>`, `email`, `role`, and an expiry (`helpdesk.jwt.ttl`, default 1h).
  - Other requests send `Authorization: Bearer <token>`. The OAuth2 resource server checks the signature, expiry and issuer, and `SecurityConfig.toAuthentication` turns the claims into a `HelpdeskUserDetails` principal **without a database lookup**. A role change or deleted user therefore only takes effect when the token expires.
  - The signing key comes from `HELPDESK_JWT_SECRET` (`helpdesk.jwt.secret`, at least 32 bytes). If it's unset, a random key is generated at startup (logged as a warning), so tokens die on restart. That's fine for dev and tests; production must set it.
  - Controllers take `@AuthenticationPrincipal HelpdeskUserDetails me` and pass `me.getId()` or `me` to services, so who is acting (reporter, author) always comes from the token, never from the request body. `POST /api/auth/register` (self sign-up as CUSTOMER), `POST /api/auth/login`, `/actuator/health/**` and `/error` are public; everything else requires authentication.
- **Authorization** happens in two places:
  - *Role rules* use `@PreAuthorize("hasAnyRole(...)")` on controller methods (`@EnableMethodSecurity`). Creating users and changing a user's role (`PATCH /api/users/{id}` with `{"role": ...}`) are ADMIN only; listing or getting users and `PATCH /api/tickets/{id}` are AGENT or ADMIN. `UserService.changeRole` rejects an admin changing their **own** role (400), so at least one admin always remains. A new role only reaches the user's JWT at their next login.
  - *Ownership rules* depend on data, so they live in services, which receive the `HelpdeskUserDetails`. Customers only see and comment on tickets they reported. Someone else's ticket gives **404, not 403**, so its existence isn't revealed. `TicketService.list` forces `reporterId` to the customer's own id.
- **Security errors** (401/403) go through `SecurityProblemHandler`, which hands them to MVC's `handlerExceptionResolver`, so `GlobalExceptionHandler` renders them as ProblemDetail like everything else. It needs explicit `AuthenticationException` and `AccessDeniedException` handlers; without them the catch-all `Exception` handler would turn a `@PreAuthorize` denial into a 500.
- **Bootstrap admin:** `AdminBootstrap` (an `ApplicationRunner`) creates an ADMIN at startup from `helpdesk.admin.email` and `helpdesk.admin.password` (`AdminProperties`, set with the `HELPDESK_ADMIN_EMAIL` and `HELPDESK_ADMIN_PASSWORD` environment variables) if that email doesn't exist. `compose.yaml` sets dev values for the `app` service, including `HELPDESK_JWT_SECRET`. With `spring-boot:run`, pass them as environment variables. Real credentials never go in committed files.

- **Kafka events:** `TicketService` publishes a `TicketEvent` (`eventId`, `type` from `TicketEventType`, `ticketId`, `actorId`, `occurredAt`, a `details` string map) through Spring's `ApplicationEventPublisher` for every *real* change: created, status or priority changed (`from`/`to`), assigned, commented. Setting a field to its current value publishes nothing. `TicketEventPublisher` forwards events with `@TransactionalEventListener`, i.e. only after the DB commit, to the topic `helpdesk.ticket-events` (3 partitions, created at startup by a `NewTopic` bean). Events are JSON (`JacksonJsonSerializer`, no type headers), keyed by ticket id so each ticket's events stay in order. Send failures are logged, never thrown: the request still succeeds, and the producer gives up after `max.block.ms=3000`. Events can be lost if the app dies between commit and send; there's no transactional outbox, by design for now.
- **Ticket history (Kafka consumer)** lives in the `activity` package. `TicketActivityListener` (`@KafkaListener`, group `helpdesk-activity`) stores each event in `ticket_activity` (V3). The table has plain `ticket_id`/`actor_id` columns with **no foreign keys**, because it's owned by the consumer, plus `details` as `jsonb` via `@JdbcTypeCode(SqlTypes.JSON)`. Processing is **idempotent**: an `existsByEventId` check, backed by a unique `event_id`, makes redeliveries and replays (offset resets) harmless. Consumer config is in `application.properties`: `ErrorHandlingDeserializer` wrapping `JacksonJsonDeserializer` with default type `TicketEvent`, no type headers, and `auto-offset-reset=earliest`. `KafkaConsumerConfig` adds a `DefaultErrorHandler` (2 retries, 1s apart, then log and skip). `GET /api/tickets/{id}/activity` (`TicketActivityService`) reuses `TicketService.get` for visibility and is **eventually consistent**. The UI's History section reloads it right after a change and again 1.5s later.

- **Search (Elasticsearch)** lives in the `search` package.
  - `TicketSearchIndexer` is a second `@KafkaListener` (group `helpdesk-search`). It treats each event only as a trigger: `TicketSearchService.index(ticketId)` re-reads the ticket and its comments from Postgres and saves a `TicketDocument` (a record; English-analyzed `title`/`description`/`comments`, keyword `status`/`priority`, `reporterId`) to the `tickets` index, or deletes it if the ticket is gone. That makes indexing idempotent and order-independent.
  - `@Document(createIndex = false)`: the app creates the index and mappings itself in `ensureIndex()`, at startup (a failure only warns) and before the first save or search. So a missing Elasticsearch never blocks startup, and documents are never saved under guessed mappings.
  - `GET /api/tickets/search?q=` builds a `NativeQuery` on `title^3`, `description`, `comments` with two `should` clauses (`minimum_should_match: 1`): a fuzzy `multi_match` (`fuzziness: AUTO`, for typos) and a `bool_prefix` `multi_match` (the last word matches as a prefix, so "print" finds "printer"; the English analyzer keeps "printer" and "print" as separate terms). Customers also get a `reporterId` term **filter**. Check analyzer behaviour with `curl localhost:9200/tickets/_analyze -d '{"field":"title","text":"..."}'` before relying on stemming. Highlights are wrapped in the private-use characters `MARK_START`/`MARK_END` (``/``), never HTML. Ids from Elasticsearch are loaded from Postgres (`TicketRepository.findByIdIn`) in ranking order, re-checking customer ownership.
  - Elasticsearch failures become `ServiceUnavailableException` (503). `management.health.elasticsearch.enabled=false` keeps `/actuator/health` UP without it. `POST /api/admin/search/reindex` (ADMIN) rebuilds the index from Postgres in pages of 100.

**Tests**, in `src/test/java`, mirror the main packages:
- `*ServiceTests`: plain Mockito unit tests with no Spring context.
- `*ControllerTests`: `@WebMvcTest` with a `@MockitoBean` service and `MockMvcTester` (AssertJ style). They `@Import(SecurityConfig.class)` so the real rules apply (a slice test doesn't pick up `@Configuration` classes on its own), and run requests as a user with `.with(user(new HelpdeskUserDetails(...)))`.
- `*RepositoryTests`: `@DataJpaTest` with `TestEntityManager`, against Testcontainers Postgres.
- `HelpdeskFlowTests` and `HelpdeskApplicationTests` also import `KafkaTestcontainersConfiguration` and `ElasticsearchTestcontainersConfiguration` (security off, 256 MB heap). These are kept separate from the Postgres config so `@DataJpaTest` doesn't start them. The two classes have **identical annotations on purpose**, so Spring's test context cache shares one context and one set of containers; keep them in sync, or test time roughly doubles. Search assertions use Awaitility, because indexing is asynchronous. `HelpdeskFlowTests.readEventTypes` reads the topic with a throwaway consumer group to assert published events.
- `HelpdeskFlowTests`: end-to-end `@SpringBootTest` + `@AutoConfigureMockMvc` tests with nothing mocked. Users register, log in at `/api/auth/login` for real, and send the bearer token (the `login(...)` helper returns a `RequestPostProcessor`). Controllers that need `TokenService` (`AuthController`) get it as a `@MockitoBean` in slice tests.

In Spring Boot 4 the test annotations live in per-module packages, e.g. `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` and `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`.

## Frontend

`frontend/` is a React 19 + TypeScript SPA built with Vite 8 and React Router 8, with no UI library and hand-written CSS in `src/index.css`. Run commands from `frontend/`:

```bash
npm install          # after cloning or when package.json changes
npm run dev          # dev server on http://localhost:5173, proxies /api to localhost:8080 (backend must be running)
npm run build        # type-check (tsc --noEmit) + production build into frontend/dist
```

- `src/api.ts`: every backend call goes through `api<T>(path, {method, body})`. It adds the bearer token from localStorage (`helpdesk.token`), throws `ApiError` carrying the ProblemDetail (`problem.errors` holds field errors) for non-2xx responses, and clears the token and logs out on a 401 for an authenticated request.
- `src/auth.tsx`: `AuthProvider` / `useAuth()` hold the current user (`undefined` while a saved token is checked with `/users/me`, `null` when logged out) and `login`, `register`, `logout`.
- `src/App.tsx`: routes. Pages that need a login sit under `RequireAuth` and use `components/Layout`. Routes are `/` (ticket list: "My tickets" for customers, filterable queue for staff), `/tickets/new`, `/tickets/:id` (detail, comments, and staff-only status/priority/assignee controls), and `/users` (admins only, behind `RequireAdmin`: create users with any role, and change roles through a per-row select that saves on change; your own row is disabled). `Layout` shows Tickets and Users (admin) `NavLink`s.
- `src/useApi.ts`: `useApi<T>(path)` loads data for a component (`{data, error, reload}`) and ignores stale responses. Use it for GETs; writes call `api()` directly and then `reload()`.
- Search: the ticket list's `SearchBox` debounces typing (300 ms) into `?q=` (with `replace`, so typing doesn't add history entries). While `q` is set, `SearchResults` (ranked, from `/tickets/search`) replaces the filters and table. `components/Highlighted` renders backend snippets by splitting on ``/`` into `<mark>` elements. **Never render search highlights or ticket text with `dangerouslySetInnerHTML`**: ticket content is user input.
- List filters live in the URL query (`useSearchParams`), not component state. Staff-only data (`/users`) is fetched in staff-only child components, because customers get 403 for it.
- `src/types.ts` mirrors the backend's response records. Update it when a DTO changes.

**How the UI is served:** in development, Vite (port 5173) serves the UI and proxies `/api`. In the Docker image, a `node:24-alpine` stage runs `npm ci && npm run build`, and the JDK stage copies `dist/` into `src/main/resources/static/` before `./mvnw package`, so the jar contains the UI and serves it on 8080 alongside the API. That folder is git-ignored and only populated inside the Docker build; a plain `./mvnw spring-boot:run` serves no UI. `SpaWebConfig` returns real static files, and returns `index.html` for any other non-`api/` path, so React Router deep links survive a reload. `SecurityConfig` requires authentication for `/api/**`, ADMIN for `/actuator/**` (except health), and permits everything else (the static UI). `src/test/resources/static/index.html` is a stand-in so `HelpdeskFlowTests` can test this.

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
- Dependencies: Spring Web MVC (`spring-boot-starter-webmvc`), Spring Data JPA, Bean Validation, Actuator, DevTools, Spring for Apache Kafka (`spring-boot-starter-kafka`), Spring Data Elasticsearch 6 (`spring-boot-starter-data-elasticsearch`, Elasticsearch 9.4), the PostgreSQL driver, and Flyway (`spring-boot-starter-flyway` plus `flyway-database-postgresql`, which Flyway needs for Postgres support). Tests use Testcontainers 2.x (`testcontainers-postgresql`, class `org.testcontainers.postgresql.PostgreSQLContainer`; the 1.x package names don't apply).
- Spring Boot 4 splits starters into finer modules, and each has its own test starter (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, etc.). When you add a starter, also add its matching `-test` starter if tests need its slices (`@WebMvcTest`, `@DataJpaTest`). Spring Boot 2/3 starter names (e.g. `spring-boot-starter-web`) don't apply here.
- `pom.xml` contains empty `<licenses>`, `<developers>`, and `<scm>` elements on purpose. They block inheritance from the parent POM, so don't remove them.

## Commands

```bash
docker compose up -d                   # start Postgres + Kafka + Elasticsearch (required before running the app)
docker compose down                    # stop them (data kept in the pgdata/kafkadata/esdata volumes; add -v to wipe)
curl -s localhost:9200/tickets/_search?pretty                                 # look at the search index directly
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic helpdesk.ticket-events --from-beginning --property print.key=true   # watch ticket events
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group helpdesk-activity                                        # consumer offsets and lag
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

The [Dockerfile](Dockerfile) is multi-stage. A Node stage builds the React UI (see Frontend), then a JDK stage runs `./mvnw package -DskipTests` (tests need Docker, which isn't available inside a build) with a BuildKit cache mount for `~/.m2`, then extracts Boot's jar layers (`-Djarmode=tools extract --layers --launcher`). The JRE stage copies the layers in order, from least to most frequently changing, runs as the non-root `spring` user, and starts with `JarLauncher`. Inside Compose the app reaches the database through `DB_URL=jdbc:postgresql://postgres:5432/helpdesk` (the service name, not `localhost`).

**CI** ([.github/workflows/ci.yml](.github/workflows/ci.yml)) runs on pushes to `main` and on pull requests. `backend` runs `./mvnw -B verify` (Testcontainers uses the runner's Docker) and uploads the surefire reports on failure. `frontend` runs `npm ci && npm run build`. The backend tests start Postgres, Kafka and Elasticsearch containers on the runner. `docker` builds the image without pushing it (GitHub Actions layer cache), and only runs after both pass. Check runs with `gh run list` / `gh run view --log-failed`. The repo is private, so Actions minutes count against the free monthly quota.

No linter or formatter is configured. Tests need Docker running (OrbStack is installed), but not `docker compose`. Testcontainers starts its own Postgres.

## Configuration

Config lives in `src/main/resources/application.properties`. The app connects to Postgres 18 from `compose.yaml` (database, user and password are all `helpdesk`). Override the connection with the `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` environment variables. Kafka (`apache/kafka:4.2.1`, single-node KRaft) is at `localhost:9092` from the host, overridden with `KAFKA_BOOTSTRAP_SERVERS`. Inside the Compose network it's `kafka:29092`, because Kafka advertises a separate listener per network. Elasticsearch (single node, security off for dev, 512 MB heap) is at `http://localhost:9200`, overridden with `ELASTICSEARCH_URIS`, or `http://elasticsearch:9200` inside Compose. The SQL Hibernate runs is logged at debug level.

**Schema changes go through Flyway** (`src/main/resources/db/migration`, Postgres SQL). Hibernate runs with `ddl-auto=validate`, so it never changes tables, and startup fails if an entity doesn't match the schema. To change an entity's columns, add a new `V<n>__description.sql` migration alongside the entity change. Never edit a migration that has already been applied. Enum columns are `varchar(20)` with `@Column(length = 20)` and no check constraints. Foreign key columns used in queries get an explicit index, because Postgres doesn't index them automatically. To query the database directly: `docker compose exec postgres psql -U helpdesk -d helpdesk`.

Tests that need a database `@Import(TestcontainersConfiguration.class)`. Its `@ServiceConnection` Postgres container overrides `spring.datasource.*`, so tests never touch the Compose database. `@WebMvcTest` and plain unit tests don't need it.

When starting the app to test changes, check whether port 8080 is already in use (the user may have their own instance running). Use a different port, e.g. `-Dspring-boot.run.arguments=--server.port=18080`, and stop only the process you started.
