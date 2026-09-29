# ADR-005: Java 25 with Spring Boot 4

## Status

Accepted

## Context

**Verified in phase 0 (2026-09-29):** the `co.com.bancolombia.cleanArchitecture` plugin **4.6.2**
accepts `--java-version=25` natively and generates a project with **Spring Boot 4.1.1**, Gradle 9.7.1,
JaCoCo 0.8.15 and PIT 1.30.0, with `JavaLanguageVersion.of(25)` in the toolchain. `./gradlew build`
passes cleanly. The risk of the scaffold not supporting Java 25 has been ruled out.

Bootstrap caveat: the system's Gradle 8.13 **cannot** run on Java 25 (it fails with
`Unsupported class file major version 69` when compiling the Groovy build scripts). The wrapper is
generated once with `JAVA_HOME` pointed at JDK 21; from then on everything runs through `./gradlew` on
Java 25.

## Decision

Target **Java 25** as the toolchain language version and let the scaffold pin **Spring Boot 4.1.1**.
Bootstrap the Gradle wrapper once using JDK 21, then use `./gradlew` exclusively for all subsequent
commands, which runs on Java 25.

## Consequences

- Developers need both a JDK 21 (bootstrap only) and a JDK 25 available locally.
- The system-installed `gradle` binary (8.13) must never be invoked after the wrapper is bootstrapped;
  only `./gradlew` is supported.
- The project benefits from Java 25 features (e.g. virtual threads) and stays on a current Spring Boot
  major version from the start, avoiding an early migration.
