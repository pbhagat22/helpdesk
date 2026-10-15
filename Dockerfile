# Multi-stage build: build the React UI with Node, compile the backend (with the UI
# inside it) using the full JDK and Maven, then copy only what's needed to run into a
# smaller JRE image. Build with `docker build -t helpdesk .`

# ---- Stage 1: build the React UI ----
FROM node:24-alpine AS frontend
WORKDIR /frontend

# package files first: `npm ci` (a clean install of exactly what package-lock.json
# lists) only reruns when dependencies change, not on every UI code change.
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

# ---- Stage 2: build the jar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Maven wrapper and pom first, then sources: Docker caches each step, so source
# changes don't invalidate the earlier layers.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src

# The built UI goes where Spring Boot serves static files from (classpath:/static/),
# so it ends up inside the jar and the app serves UI and API from one address.
COPY --from=frontend /frontend/dist src/main/resources/static

# The cache mount keeps ~/.m2 between builds, so dependencies are downloaded once.
# Tests are skipped here: they need Docker (Testcontainers), which isn't available
# inside a build. They run with `./mvnw test` locally and in CI (roadmap stage 6).
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

# Split the jar into layers (dependencies change rarely, application code often),
# so a code change only rebuilds and re-pushes the small application layer.
RUN java -Djarmode=tools -jar target/helpdesk-*.jar extract --layers --launcher --destination extracted

# ---- Stage 3: runtime image ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Don't run as root: if the app is compromised, the attacker isn't root in the container.
RUN useradd --system --uid 1001 spring
USER spring

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# The JVM sizes its heap from the container's memory limit; use 75% of it
# instead of the default 25%.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
