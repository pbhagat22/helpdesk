# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Freshly generated Spring Boot project (Spring Initializr). Only the `@SpringBootApplication` entry point (`com.example.helpdesk.HelpdeskApplication`) and a `contextLoads` smoke test exist. There's no domain code, controllers, or persistence layer yet. Update this file as the architecture takes shape.

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
- Dependencies: Spring Web MVC (`spring-boot-starter-webmvc`), Spring Data JPA, Bean Validation, Actuator, DevTools, and H2 (in-memory, runtime scope) with the H2 console module (`spring-boot-h2console`).
- Spring Boot 4 splits starters into finer modules, and each has its own test starter (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, etc.). When you add a starter, also add its matching `-test` starter if tests need its slices (`@WebMvcTest`, `@DataJpaTest`). Spring Boot 2/3 starter names (e.g. `spring-boot-starter-web`) don't apply here.
- `pom.xml` contains empty `<licenses>`, `<developers>`, and `<scm>` elements on purpose. They block inheritance from the parent POM, so don't remove them.

## Commands

```bash
./mvnw spring-boot:run                 # run the app (port 8080 by default)
./mvnw test                            # run all tests
./mvnw test -Dtest=HelpdeskApplicationTests            # run a single test class
./mvnw test -Dtest=HelpdeskApplicationTests#contextLoads  # run a single test method
./mvnw clean package                   # build the jar into target/
./mvnw spring-boot:build-image         # build an OCI image
```

No linter or formatter is configured.

## Configuration

Config lives in `src/main/resources/application.properties` (currently just `spring.application.name=helpdesk`). With no datasource configured, H2 runs as an auto-configured in-memory database, so data is lost on restart.
