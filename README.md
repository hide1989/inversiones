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
