# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage: compiles the Gradle multi-module project with the repo's own
# Gradle wrapper. The system Gradle 8.13 cannot run on Java 25 (see
# docs/adr/ADR-005-java-25-spring-boot-4.md), which is why the wrapper (and
# this JDK image) is used instead of a `gradle:*-jdk` base image.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Layer 1: wrapper + build scripts only, so a source-only change below does not
# invalidate the dependency-download layer.
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle settings.gradle main.gradle ./
COPY applications/app-service/build.gradle applications/app-service/build.gradle
COPY domain/model/build.gradle domain/model/build.gradle
COPY domain/usecase/build.gradle domain/usecase/build.gradle
COPY infrastructure/driven-adapters/jpa-repository/build.gradle infrastructure/driven-adapters/jpa-repository/build.gradle
COPY infrastructure/driven-adapters/rest-consumer/build.gradle infrastructure/driven-adapters/rest-consumer/build.gradle
COPY infrastructure/driven-adapters/security-jwt/build.gradle infrastructure/driven-adapters/security-jwt/build.gradle
COPY infrastructure/entry-points/api-rest/build.gradle infrastructure/entry-points/api-rest/build.gradle
RUN chmod +x gradlew

# Warm the dependency cache. Any module's compileClasspath resolution pulls in
# the full dependency set, so this fails soft rather than requiring every task
# to be named; the real build below is what must succeed.
RUN ./gradlew --no-daemon dependencies || true

# Layer 2: the actual source, then build the boot jar. `validateStructure` is
# named explicitly here, not assumed from `bootJar`'s own task graph: a prior
# version of this file claimed `validateStructure` "runs as part of `build`'s
# task graph", but the command below never invoked `build` at all, so that
# check never ran and a domain module that picked up a framework dependency
# would have produced a green image build. Naming the task directly makes the
# claim true instead of removing it, since the whole point of running this
# check at image-build time is to fail the image, not just the local build,
# when a module crosses a layer it must not.
COPY . .
RUN ./gradlew --no-daemon validateStructure :app-service:bootJar -x test -x acceptanceTest

# ---------------------------------------------------------------------------
# Runtime stage: JRE only, non-root, sized to the container's memory limit.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# curl is not present in the base JRE image; it is needed for HEALTHCHECK below.
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system inversiones \
    && useradd --system --gid inversiones --no-create-home inversiones

WORKDIR /app
COPY --from=build /workspace/applications/app-service/build/libs/inversiones.jar /app/inversiones.jar

RUN chown -R inversiones:inversiones /app
USER inversiones

EXPOSE 8080

# -XX:MaxRAMPercentage=75 makes the JVM heap a fraction of the *container's*
# memory limit (cgroup) rather than the host's total memory, which matters any
# time the container is given less memory than the Docker host has.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -sf http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/inversiones.jar"]
