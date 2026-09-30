# Inversiones

A personal investments dashboard: portfolio tracking, position P&L, combined technical/news/analyst
signals, and AI-assisted synthesis across a multi-broker, multi-currency (COP/USD) portfolio.

This repository is a rebuild of a system that today runs as a single static HTML file backed by n8n
workflows. It is being migrated to a Java clean-architecture backend and a React frontend, while n8n
stays in place as the data ingestion layer (see `docs/adr/ADR-004-n8n-as-ingestion-layer.md`).

## Module layout

```
inversiones/
├── spec.md                       Planning specification (Spanish)
├── README.md                     This file
├── docs/
│   └── adr/                      Architecture Decision Records (ADR-001 .. ADR-005)
├── backend/                      Gradle multi-module, clean architecture, Java 25
│   ├── applications/app-service/        Spring Boot main, DI wiring, application properties
│   ├── domain/
│   │   ├── model/                       Entities, value objects, ports. Zero framework dependencies
│   │   └── usecase/                     Business logic. Zero framework dependencies
│   ├── infrastructure/
│   │   ├── driven-adapters/             JPA/PostgreSQL, REST consumer (n8n), security/JWT
│   │   ├── entry-points/                REST controllers (api-rest)
│   │   └── helpers/
│   └── deployment/                      Dockerfile
├── frontend/                     Planned: Vite + React 19 + TypeScript dashboard (not created yet)
├── deploy/                       Production docker-compose, nginx config, env template
└── jenkins/                      CI/CD: docker-compose, Configuration as Code, plugins
```

The dependency rule is enforced by the clean architecture scaffold: `domain/model` and `domain/usecase`
never compile against Spring, JPA, or any other framework. `./gradlew validateStructure` verifies this
in the build pipeline.

## Prerequisites

- **Java 25** (JDK 25) — required to run `./gradlew` after the wrapper has been bootstrapped.
- **JDK 21** — needed only once, to bootstrap the Gradle wrapper (the system Gradle 8.13 cannot run
  on Java 25).
- **Docker** — for PostgreSQL via Testcontainers in tests, and for local/production deployment.
- **Node 22** — for the frontend, once it exists.

## Run the backend locally

One command brings up PostgreSQL and the API together, built from source, with no cloud account,
VPS access or credential beyond what is in this repo.

**Prerequisites**

- **Docker** with Compose v2 (`docker compose version`).
- **Java 25** is only needed if you want to run `./gradlew` on the host (for the test suites below).
  The Docker build compiles inside the `eclipse-temurin:25-jdk` image, so a working Java 25 install
  on the host is not required just to run the stack.
- The system-wide `gradle` (if installed) cannot be used for this project (Gradle 8.13 cannot run on
  Java 25) — always use the wrapper, `backend/gradlew`, which the Docker build also uses internally.

**Start everything**

```bash
# Copy the template once; the defaults work as-is for local use.
cp deploy/.env.template deploy/.env

docker compose -f deploy/docker-compose.dev.yml up -d --build
```

This builds the backend image (multi-stage: `eclipse-temurin:25-jdk` to compile with the Gradle
wrapper, `eclipse-temurin:25-jre` to run), starts PostgreSQL, waits for it to report healthy, then
starts the backend, which runs Flyway migrations and seeds reference data on boot. The backend
listens on `http://localhost:8080`.

**Verify it's working**

```bash
bash scripts/smoke.sh
```

This waits for `/actuator/health` to report `UP` (bounded timeout, clear failure message), logs in
with the seeded local credentials, calls `GET /api/v1/portfolio` with the resulting token and checks
the response declares a base currency and contains amounts, then calls the same endpoint with **no**
token and checks it is rejected — so the smoke test cannot pass against an unsecured deployment. It
exits non-zero, naming the failed step, if anything is wrong. It accepts a base URL as an argument or
via `BASE_URL`, defaulting to `http://localhost:8080`, so the same script can be pointed at a
deployed environment later.

**Seeded local credentials:** username `david`, password `Inversiones2026!`. These are deliberately
local-only — the BCrypt hash lives in `V2__seed_reference_data.sql` and is not a production secret.

**n8n dependency:** the application does not contact n8n at startup, so it boots and passes the smoke
test with no network access to the ingestion VPS. Endpoints that proxy live market/news/analyst data
(prices, technicals, sentiment, etc.) need n8n reachable at `N8N_BASE_URL` to return real data; without
it they degrade gracefully (empty/cached data, not an error) rather than failing the boot.

**Run the three test suites**

```bash
cd backend

# Unit tests (274 tests across all modules) — no Docker required
./gradlew test --no-configuration-cache

# Cucumber acceptance suite (10 scenarios against the real HTTP API, Testcontainers PostgreSQL,
# WireMock standing in for n8n) — requires Docker
./gradlew acceptanceTest --no-configuration-cache

# Full build: unit tests, JaCoCo merged coverage, PIT mutation testing, structure validation
./gradlew build --no-configuration-cache
```

**Stop and reset**

```bash
# Stop the containers, keep the database volume (data survives a restart)
docker compose -f deploy/docker-compose.dev.yml down

# Stop and wipe everything, including the PostgreSQL volume (full reset)
docker compose -f deploy/docker-compose.dev.yml down -v
```

**How `POSTGRES_HOST` is resolved for both cases:** `application-local.yml` defaults
`POSTGRES_HOST` to `localhost`, which is correct when running the backend directly on the host (for
example via `./gradlew bootRun`, against the `postgres` container's published port). Inside the
Compose network, `localhost` would resolve to the backend container itself rather than the database,
so `deploy/docker-compose.dev.yml` overrides `POSTGRES_HOST=postgres` (the service name) for the
`backend` service only — the host-run case is untouched.

## Backend: build and test

All commands run from `backend/`, using the wrapper (`./gradlew`), never the system `gradle`:

```bash
cd backend

# Full build: compile, unit tests, JaCoCo coverage, PIT mutation testing, structure validation
./gradlew build

# Structure validation only (fails if domain modules pick up a framework dependency)
./gradlew validateStructure

# Run tests only
./gradlew test
```

Note: the very first time this repository is cloned, if `backend/gradle/wrapper/gradle-wrapper.jar` is
missing, the wrapper must be bootstrapped once with JDK 21 before `./gradlew` will work — see
`docs/adr/ADR-005-java-25-spring-boot-4.md` for the exact command.

## Frontend: build and test

The frontend does not exist yet. It is planned as a Vite + React 19 + TypeScript application living in
`frontend/`, deployed to Vercel (see `docs/adr/ADR-002-frontend-vercel-backend-vps.md`). Its build and
test commands will be documented here once the project is scaffolded.

## Architecture decisions

See `docs/adr/` for the recorded decisions and their rationale:

- `ADR-001-postgresql-over-mongodb.md`
- `ADR-002-frontend-vercel-backend-vps.md`
- `ADR-003-imperative-over-reactive.md`
- `ADR-004-n8n-as-ingestion-layer.md`
- `ADR-005-java-25-spring-boot-4.md`
