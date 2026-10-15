# Helpdesk

[![CI](https://github.com/pbhagat22/helpdesk/actions/workflows/ci.yml/badge.svg)](https://github.com/pbhagat22/helpdesk/actions/workflows/ci.yml)

A support helpdesk: customers sign up and file tickets, and agents work through a queue, assign tickets, change their status and reply.

- **Backend:** Spring Boot 4 (Java 25), Spring Security with JWT login, Spring Data JPA, Flyway, PostgreSQL
- **Frontend:** React 19 + TypeScript, built with Vite
- **Packaging:** one Docker image serving both the UI and the REST API
- **CI:** GitHub Actions builds and tests every push (tests run against real Postgres with Testcontainers)

## Run it

Requires Docker.

```bash
docker compose --profile app up -d --build --wait
```

Open http://localhost:8080 and sign up, or log in as the development admin: `admin@example.com` / `admin-password`.

Stop it with `docker compose --profile app down` (add `-v` to also delete the data).

## Develop

Requires Java 25, Node.js 24+ and Docker.

```bash
docker compose up -d                       # Postgres only
HELPDESK_ADMIN_EMAIL=admin@example.com HELPDESK_ADMIN_PASSWORD=admin-password ./mvnw spring-boot:run   # API on :8080
cd frontend && npm install && npm run dev  # UI on http://localhost:5173, hot reload
```

```bash
./mvnw test                                # backend tests
cd frontend && npm run build               # type-check + build the UI
```

## Roles

| | Customer | Agent | Admin |
|---|---|---|---|
| File tickets, comment on own tickets | ✅ | ✅ | ✅ |
| See all tickets, assign, change status | | ✅ | ✅ |
| Create agent and admin accounts | | | ✅ |
