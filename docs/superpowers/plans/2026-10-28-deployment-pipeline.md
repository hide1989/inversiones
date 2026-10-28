# Deployment Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Jenkins pipeline that builds, tests and deploys this project — frontend to Vercel, backend and Postgres to the Hostinger VPS behind the Traefik already running there — validated end to end with `DEPLOY_TARGET=local` so the real deploy is one parameter away.

**Architecture:** The backend image already exists (`backend/deployment/Dockerfile`, multi-stage Temurin 25, non-root, HEALTHCHECK, `MaxRAMPercentage=75`). This plan adds the production Spring configuration the app does not yet have, a production compose file that joins the VPS's existing Traefik network with explicit memory limits, a `vercel.json` for the SPA, and a declarative `Jenkinsfile` whose deploy target is a parameter. Jenkins itself runs from `jenkins/docker-compose.yml` with Configuration as Code so the same setup reproduces on the Mac and later on the VPS.

**Tech Stack:** Docker Compose v5, Traefik (already deployed), Jenkins LTS with JCasC, Vercel CLI, Gradle wrapper 9.7.1 on Java 25, Vite 8.

**Spec:** `spec.md` §7 (Despliegue). Design delta approved in conversation on 2026-10-28; the VPS facts below were verified over SSH, not assumed.

## Global Constraints

- **The VPS has 7.8 GB RAM, 5.2 GB available, 2 CPUs, and NO SWAP (0 B).** Seven containers run there and **none has a memory limit**. Every container this plan adds MUST declare one.
- Traefik network: **`n8n_default`** (verified). Entrypoints: **`web`** (:80, redirects to websecure) and **`websecure`** (:443). Cert resolver: **`mytlschallenge`** (TLS-ALPN-01). Traefik runs with `exposedbydefault=false`, so **`traefik.enable=true` is mandatory** on every routed container.
- `*.srv1449561.hstgr.cloud` is a **DNS wildcard** resolving to `148.230.88.179` (verified). No DNS change is needed for any subdomain. The backend host is **`api.srv1449561.hstgr.cloud`**.
- VPS compose stacks live in `/docker/<project>`. This one goes in **`/docker/inversiones`**.
- **The Postgres port is never published in production.** Only the backend reaches it, over the compose network.
- No secret in Git. `deploy/.env` and `frontend/.env` stay git-ignored; `.env.template` is tracked.
- The committed local JWT default (`local-development-secret-change-me-32bytes`) must remain rejected outside the `local` profile. `JwtCodecConfig` already enforces this — do not weaken it.
- Code, comments, commit messages and test names in English. Conventional commits.
- The Gradle wrapper at `backend/gradlew` is mandatory; the system Gradle 8.13 cannot run on Java 25.

## Review Focus

Five failure modes the spec implies that no task's happy-path tests would catch. Each line's test is added to the task that owns the code.

1. **The JVM sizes its heap from the host, not the container, and the OOM killer takes n8n.** With `MaxRAMPercentage=75` and no container limit, the heap targets ~5.9 GB of the host's 7.8 GB. There is no swap, so the kernel kills a process — plausibly n8n, which is this application's only data source. Owned by Task 3, whose test asserts the limit is present and that the JVM reports a heap sized from it.
2. **`JWT_SECRET` unset in production boots a broken app instead of failing fast.** `application.yml` declares `${JWT_SECRET}` with no default, so a missing value must stop startup with a legible error rather than surface later as 500s on login. Owned by Task 1.
3. **`SECURITY_ALLOWED_ORIGINS` left at its development default silently blocks the deployed frontend.** The browser reports an opaque CORS failure and the API logs nothing wrong. Owned by Task 1.
4. **Postgres published to the host in production exposes the database to the internet.** The dev compose publishes 5432 deliberately so pgAdmin can reach it; copying that file into production carries the port with it. Owned by Task 3.
5. **A pipeline stage that passes because it ran nothing.** `BUILD SUCCESSFUL` from an up-to-date Gradle task, a vitest run whose file failed to compile, a smoke test against a URL that was never up. Owned by Task 4, whose tests assert each stage fails when its subject is broken.

---

## One contradiction in the spec, and how it is resolved

`spec.md` §3's repository listing includes `deploy/nginx/inversiones.conf`, but §7.2 explicitly rejects a dedicated Nginx: *"En lugar de añadir un Nginx propio, el backend se une a la red de ese Traefik y expone su ruta con labels, heredando el TLS de Let's Encrypt ya configurado."* §7.2 is the later, reasoned decision and carries the note "Verificado en la fase 0", so it wins. **No Nginx configuration is created by this plan**, and §3's listing is stale on that line. Confirmed on the VPS: Traefik owns :80 and :443, so a second reverse proxy could not bind them anyway.

## File Structure

| File | Responsibility |
|---|---|
| `backend/applications/app-service/src/main/resources/application.yml` | Gains the `spring.datasource` block with env placeholders, so any profile can connect. |
| `backend/applications/app-service/src/main/resources/application-local.yml` | Keeps only the development defaults it overrides. |
| `backend/applications/app-service/src/test/java/.../config/ProductionConfigurationTest.java` | Asserts the context fails fast without `JWT_SECRET` and that the datasource resolves from env. |
| `frontend/vercel.json` | SPA rewrite and security headers for the static build. |
| `frontend/src/__tests__/vercelConfig.test.ts` | Asserts the rewrite and each header are present. |
| `deploy/docker-compose.prod.yml` | Backend + Postgres, Traefik labels, memory limits, unpublished DB port. |
| `deploy/.env.template` | Gains the production variables. |
| `deploy/backup/pg_dump_daily.sh` | Daily `pg_dump` with retention, installed by cron on the VPS. |
| `Jenkinsfile` | Declarative pipeline, nine stages, `DEPLOY_TARGET` parameter. |
| `jenkins/docker-compose.yml` | Jenkins LTS with JCasC mounted. |
| `jenkins/casc/jenkins.yaml` | Jobs, credentials bindings and tool config as code. |
| `jenkins/plugins.txt` | Pinned plugin set. |
| `docs/runbook-vps.md` | The manual steps that need credentials: Vercel account, VPS first deploy, webhook. |

---

## Task 1: Production Spring configuration

**Files:**
- Modify: `backend/applications/app-service/src/main/resources/application.yml`
- Modify: `backend/applications/app-service/src/main/resources/application-local.yml`
- Create: `backend/applications/app-service/src/test/java/co/com/david/inversiones/config/ProductionConfigurationTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: the env var contract every later task depends on — `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`, `SECURITY_ALLOWED_ORIGINS`.

**Why this task exists.** `application.yml` has no `spring.datasource` block at all today; the connection lives only in `application-local.yml`. Starting with any other profile yields an app with no datasource, which fails in a confusing way far from the cause. No code changes — `CorsConfig` already reads `inversiones.security.allowed-origins` as a `List<String>` from a property, and Spring binds a comma-separated env var to it, so CORS needs only the variable.

- [ ] **Step 1: Write the failing test**

Create `backend/applications/app-service/src/test/java/co/com/david/inversiones/config/ProductionConfigurationTest.java`:

```java
package co.com.david.inversiones.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The production configuration contract. These assertions are about what happens when an
 * environment variable is MISSING or WRONG, which is the only way this configuration is ever
 * actually exercised in anger — a deployment that forgot one.
 */
class ProductionConfigurationTest {

    private static final String PROD_PROFILE = "prod-test";

    @Test
    @DisplayName("the datasource url resolves from environment variables outside the local profile")
    void datasourceResolvesFromEnvironmentOutsideLocalProfile() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(PROD_PROFILE);
        environment.getSystemProperties().put("POSTGRES_HOST", "db.example.test");
        environment.getSystemProperties().put("POSTGRES_PORT", "6000");
        environment.getSystemProperties().put("POSTGRES_DB", "inv_prod");
        environment.getSystemProperties().put("JWT_SECRET", "a-thirty-two-byte-minimum-secret!!");

        SpringApplication application = new SpringApplication(TestOnlyConfigLoader.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);

        try (var context = application.run()) {
            String url = context.getEnvironment().getProperty("spring.datasource.url");
            assertThat(url)
                    .as("application.yml must carry the datasource, not only application-local.yml")
                    .isEqualTo("jdbc:postgresql://db.example.test:6000/inv_prod");
        }
    }

    @Test
    @DisplayName("a missing JWT_SECRET fails startup with a message naming the property")
    void missingJwtSecretFailsStartup() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(PROD_PROFILE);

        SpringApplication application = new SpringApplication(TestOnlyConfigLoader.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);

        assertThatThrownBy(application::run)
                .as("an unset JWT_SECRET must stop startup, not surface later as a 500 on login")
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("allowed origins binds a comma-separated list, so one variable carries several hosts")
    void allowedOriginsBindsACommaSeparatedList() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(PROD_PROFILE);
        environment.getSystemProperties().put("JWT_SECRET", "a-thirty-two-byte-minimum-secret!!");
        environment.getSystemProperties()
                .put("SECURITY_ALLOWED_ORIGINS", "https://a.vercel.app,https://b.vercel.app");

        SpringApplication application = new SpringApplication(TestOnlyConfigLoader.class);
        application.setEnvironment(environment);
        application.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);

        try (var context = application.run()) {
            String[] origins = context.getEnvironment()
                    .getProperty("inversiones.security.allowed-origins", String[].class);
            assertThat(origins).containsExactly("https://a.vercel.app", "https://b.vercel.app");
        }
    }

    @org.springframework.boot.autoconfigure.SpringBootApplication(
            exclude = {
                org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class,
                org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration.class
            })
    static class TestOnlyConfigLoader {
    }
}
```

Create `backend/applications/app-service/src/test/resources/application-prod-test.yml` holding only:

```yaml
# A stand-in for a real deployment profile: no datasource defaults of its own, so these tests
# prove the values come from application.yml and the environment rather than from a profile file.
inversiones:
  security:
    jwt-secret: ${JWT_SECRET}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd backend
./gradlew :app-service:test --tests '*ProductionConfigurationTest*' --rerun-tasks
```

Expected: `datasourceResolvesFromEnvironmentOutsideLocalProfile` FAILS because `spring.datasource.url` is null — `application.yml` has no datasource block.

Note: `--rerun-tasks` is not optional. Without it Gradle reports `BUILD SUCCESSFUL` from an up-to-date task and the tests never run, which reads exactly like a pass.

- [ ] **Step 3: Move the datasource into `application.yml`**

In `application.yml`, add under the existing `spring:` key, immediately before `jpa:`:

```yaml
  datasource:
    # The connection lives here, not in a profile, so every profile can connect. Development
    # defaults are supplied by application-local.yml, which overrides only what differs. Placing
    # this in the profile instead left any non-local profile with no datasource at all, failing
    # far from the cause.
    url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT:5432}/${POSTGRES_DB:inversiones}
    username: ${POSTGRES_USER:inversiones}
    password: ${POSTGRES_PASSWORD}
```

`POSTGRES_HOST` and `POSTGRES_PASSWORD` deliberately carry no default: a deployment that forgets either must fail at startup rather than quietly target localhost with a guessable password.

- [ ] **Step 4: Reduce `application-local.yml` to its overrides**

Replace the `spring.datasource` block in `application-local.yml` with:

```yaml
spring:
  datasource:
    # Development defaults only. The url, username and password shapes live in application.yml;
    # these three values are what make `bootRun` and the dev compose work with no setup step.
    # POSTGRES_PORT defaults to 5432 here and in application.yml, but this machine's compose maps
    # Postgres to 5433 because an unrelated container holds 5432 — see deploy/.env.
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:inversiones}
    username: ${POSTGRES_USER:inversiones}
    password: ${POSTGRES_PASSWORD:inversiones_local}
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd backend
./gradlew :app-service:test --tests '*ProductionConfigurationTest*' --rerun-tasks
```

Expected: 3 tests PASS.

- [ ] **Step 6: Verify local development still works unchanged**

```bash
cd backend
POSTGRES_PORT=5433 ./gradlew :app-service:bootRun --no-daemon &
sleep 45
curl -s localhost:8080/actuator/health
```

Expected: `{"groups":["liveness","readiness"],"status":"UP"}`. Then stop it with `kill %1`.

Use `--no-daemon`: a warm Gradle daemon from an earlier invocation does not see an environment variable set on this command line, which is exactly how the `POSTGRES_PORT` fix appears not to take effect.

- [ ] **Step 7: Run the whole backend suite**

```bash
cd backend
./gradlew build --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`, 289+ tests, the merged JaCoCo gate at 80% satisfied.

- [ ] **Step 8: Commit**

```bash
git add backend/applications/app-service/src/main/resources/application.yml \
        backend/applications/app-service/src/main/resources/application-local.yml \
        backend/applications/app-service/src/test/java/co/com/david/inversiones/config/ProductionConfigurationTest.java \
        backend/applications/app-service/src/test/resources/application-prod-test.yml
git commit -m "feat(backend): make the datasource configurable outside the local profile

The connection lived only in application-local.yml, so any other profile
started with no datasource and failed far from the cause. It now lives in
application.yml with environment placeholders, and application-local.yml
keeps only the development defaults it overrides.

POSTGRES_HOST and POSTGRES_PASSWORD carry no default on purpose: a
deployment that forgets either fails at startup instead of quietly
targeting localhost with a guessable password."
```

---

## Task 2: Vercel configuration for the SPA

**Files:**
- Create: `frontend/vercel.json`
- Create: `frontend/src/__tests__/vercelConfig.test.ts`

**Interfaces:**
- Consumes: the env var contract from Task 1 (`SECURITY_ALLOWED_ORIGINS` must later list the Vercel origin).
- Produces: `frontend/vercel.json` with `rewrites` and `headers`, consumed by Task 4's frontend deploy stage.

**Why this task exists.** A React Router SPA served as static files 404s on every deep link unless the host rewrites unknown paths to `index.html`. A test is worth writing because this file is unexercised until someone opens `/posiciones` directly in production, by which point the mistake is public.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/__tests__/vercelConfig.test.ts`:

```ts
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

// vercel.json is never exercised by the dev server or by any other test: its first real use is a
// production deep link. These assertions are the only thing standing between a typo here and a
// 404 on every route but "/".
describe('vercel.json', () => {
  const config = JSON.parse(
    readFileSync(resolve(__dirname, '../../vercel.json'), 'utf-8'),
  ) as {
    rewrites?: Array<{ source: string; destination: string }>
    headers?: Array<{ source: string; headers: Array<{ key: string; value: string }> }>
  }

  it('rewrites every unmatched path to index.html so deep links resolve', () => {
    expect(config.rewrites).toBeDefined()
    const spaFallback = config.rewrites?.find((r) => r.destination === '/index.html')
    expect(spaFallback, 'no rewrite targets /index.html').toBeDefined()
    expect(spaFallback?.source).toBe('/(.*)')
  })

  it('sends the security headers the spec requires', () => {
    const forAllPaths = config.headers?.find((h) => h.source === '/(.*)')
    expect(forAllPaths, 'no header block covers all paths').toBeDefined()
    const keys = (forAllPaths?.headers ?? []).map((h) => h.key)
    expect(keys).toEqual(
      expect.arrayContaining([
        'X-Content-Type-Options',
        'X-Frame-Options',
        'Referrer-Policy',
        'Strict-Transport-Security',
      ]),
    )
  })

  it('does not weaken X-Frame-Options or nosniff', () => {
    const forAllPaths = config.headers?.find((h) => h.source === '/(.*)')
    const byKey = new Map((forAllPaths?.headers ?? []).map((h) => [h.key, h.value]))
    expect(byKey.get('X-Content-Type-Options')).toBe('nosniff')
    expect(byKey.get('X-Frame-Options')).toBe('DENY')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd frontend
npx vitest run src/__tests__/vercelConfig.test.ts 2>&1 | tail -14
```

Expected: FAIL — `vercel.json` does not exist, so the read throws `ENOENT`.

Read the summary with `tail`, never `grep "Test Files"`: vitest emits ANSI escapes inside that phrase, so a plain grep returns empty, which reads exactly like "no failures found".

- [ ] **Step 3: Create `frontend/vercel.json`**

```json
{
  "$schema": "https://openapi.vercel.sh/vercel.json",
  "buildCommand": "npm run build",
  "outputDirectory": "dist",
  "framework": "vite",
  "rewrites": [
    {
      "source": "/(.*)",
      "destination": "/index.html"
    }
  ],
  "headers": [
    {
      "source": "/(.*)",
      "headers": [
        { "key": "X-Content-Type-Options", "value": "nosniff" },
        { "key": "X-Frame-Options", "value": "DENY" },
        { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" },
        { "key": "Strict-Transport-Security", "value": "max-age=63072000; includeSubDomains" },
        { "key": "Permissions-Policy", "value": "camera=(), microphone=(), geolocation=()" }
      ]
    }
  ]
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd frontend
npx vitest run src/__tests__/vercelConfig.test.ts 2>&1 | tail -14
```

Expected: 3 tests PASS.

- [ ] **Step 5: Prove the test has teeth**

Temporarily change `"destination": "/index.html"` to `"/wrong.html"`, re-run, confirm the first test FAILS, then restore it. A test that only reads a file it also validates can pass against a file that says anything; this proves it does not.

- [ ] **Step 6: Verify the production build still succeeds**

```bash
cd frontend
npm run build 2>&1 | tail -5
```

Expected: build succeeds and `dist/index.html` exists.

- [ ] **Step 7: Commit**

```bash
git add frontend/vercel.json frontend/src/__tests__/vercelConfig.test.ts
git commit -m "feat(frontend): add the Vercel configuration for the SPA

Without the rewrite to index.html every route but / 404s when the static
build is served, and the first time anyone notices is a deep link in
production. The test exists because this file has no other exercise: the
dev server never reads it.

Also sets nosniff, DENY framing, a referrer policy, HSTS and a
permissions policy for every path."
```

---

## Task 3: Production compose file with memory limits

**Files:**
- Create: `deploy/docker-compose.prod.yml`
- Modify: `deploy/.env.template`
- Create: `deploy/__tests__/compose-prod.bats` (or a shell test; see Step 1)

**Interfaces:**
- Consumes: the env var contract from Task 1, and `backend/deployment/Dockerfile` (already exists, unchanged).
- Produces: a compose file Task 4's deploy stage invokes, and the service name `backend` that the smoke stage health-checks.

**Why this task exists, and the one thing it must get right.** The VPS has **no swap** and **none of its seven existing containers has a memory limit**. `backend/deployment/Dockerfile` sets `-XX:MaxRAMPercentage=75`, which sizes the heap from the *container's* cgroup limit — and with no limit, from the host's 7.8 GB, targeting ~5.9 GB. Deploying that would make the kernel kill a process, plausibly n8n, which is this application's only source of market data. The limits in this file are the fix, and the test asserts they are there.

- [ ] **Step 1: Write the failing test**

Create `deploy/__tests__/compose-prod.sh`, executable:

```bash
#!/usr/bin/env bash
# Asserts the production compose file carries the properties that keep it from damaging the VPS
# it deploys onto. Every check here is something that is silently fine in development and
# expensive in production, so none of them has another test anywhere.
set -uo pipefail

COMPOSE="$(cd "$(dirname "$0")/.." && pwd)/docker-compose.prod.yml"
fail=0
check() { # check <description> <condition-exit-code>
  if [ "$2" -eq 0 ]; then printf '  ok   %s\n' "$1"; else printf '  FAIL %s\n' "$1"; fail=1; fi
}

# Render the file the way Docker itself would, so these assertions are about the effective
# configuration rather than the YAML text. A missing variable must not fail the render.
rendered="$(POSTGRES_PASSWORD=x JWT_SECRET=y BACKEND_HOST=h.example \
  SECURITY_ALLOWED_ORIGINS=https://x.vercel.app N8N_BASE_URL=https://n8n.example/webhook/inv \
  docker compose -f "$COMPOSE" config 2>/dev/null)"
check "the file renders with docker compose config" $?

grep -q 'mem_limit\|memory:' <<<"$rendered"
check "a memory limit is declared (the VPS has no swap and 7.8 GB total)" $?

# The backend's own limit, specifically: MaxRAMPercentage=75 reads the container limit, so an
# absent one makes the JVM target 75% of the HOST's memory.
python3 - "$COMPOSE" <<'PY'
import sys, re
text = open(sys.argv[1]).read()
backend = text.split('backend:', 1)[1] if 'backend:' in text else ''
backend = backend.split('\n  postgres:', 1)[0]
sys.exit(0 if re.search(r'mem_limit:\s*\S+', backend) else 1)
PY
check "the BACKEND declares mem_limit" $?

! grep -qE '^\s+-\s+"?[0-9]+:5432"?' <<<"$rendered"
check "Postgres does NOT publish a host port (it would face the internet)" $?

grep -q 'traefik.enable' <<<"$rendered"
check "traefik.enable is set (Traefik runs with exposedbydefault=false)" $?

grep -q 'n8n_default' <<<"$rendered"
check "the stack joins the existing n8n_default network" $?

grep -q 'mytlschallenge' <<<"$rendered"
check "the cert resolver matches the one Traefik runs (mytlschallenge)" $?

grep -q 'external: true' <<<"$rendered"
check "the Traefik network is referenced as external, not created" $?

exit "$fail"
```

Make it executable: `chmod +x deploy/__tests__/compose-prod.sh`

- [ ] **Step 2: Run the test to verify it fails**

```bash
./deploy/__tests__/compose-prod.sh
```

Expected: every check FAILS — `docker-compose.prod.yml` does not exist.

- [ ] **Step 3: Create `deploy/docker-compose.prod.yml`**

```yaml
# Production stack for the Hostinger VPS. Deployed to /docker/inversiones, alongside the
# /docker/n8n and /docker/social-autopost stacks already there.
#
# Two properties of that machine shaped this file, both verified on it rather than assumed:
#
#   1. It has 7.8 GB of RAM, 5.2 GB available, and NO SWAP. None of the seven containers already
#      running declares a memory limit. The backend image sets -XX:MaxRAMPercentage=75, which
#      sizes the heap from the CONTAINER's cgroup limit — so with no limit the JVM would target
#      75% of the host's 7.8 GB, and with no swap the kernel would kill a process to get it back.
#      The most likely victim is n8n, which is this application's only source of market data.
#      Hence mem_limit on both services.
#
#   2. Traefik already runs there as n8n-traefik-1 on the n8n_default network, owning :80 and
#      :443 with a Let's Encrypt resolver named mytlschallenge. This stack joins that network and
#      routes by label instead of adding a second reverse proxy and a second certificate to renew.
#      Traefik runs with --providers.docker.exposedbydefault=false, so traefik.enable=true is
#      required, not decorative.

services:
  backend:
    build:
      context: ../backend
      dockerfile: deployment/Dockerfile
    image: inversiones-backend:${IMAGE_TAG:-latest}
    container_name: inversiones-backend
    restart: unless-stopped
    # 1 GB gives the JVM a ~750 MB heap at MaxRAMPercentage=75, which this workload fits in with
    # room to spare, and leaves the rest of the machine to n8n and the social stack.
    mem_limit: 1g
    memswap_limit: 1g
    environment:
      SPRING_PROFILES_ACTIVE: prod
      POSTGRES_HOST: postgres
      POSTGRES_PORT: 5432
      POSTGRES_DB: ${POSTGRES_DB:-inversiones}
      POSTGRES_USER: ${POSTGRES_USER:-inversiones}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
      JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}
      JWT_EXPIRATION_MINUTES: ${JWT_EXPIRATION_MINUTES:-480}
      # The deployed frontend's origin. Wrong or missing, the browser reports an opaque CORS
      # failure and this API logs nothing wrong, so it is worth getting right the first time.
      SECURITY_ALLOWED_ORIGINS: ${SECURITY_ALLOWED_ORIGINS:?SECURITY_ALLOWED_ORIGINS is required}
      N8N_BASE_URL: ${N8N_BASE_URL:-http://n8n:5678/webhook/inv}
      N8N_SLOW_READ_TIMEOUT: ${N8N_SLOW_READ_TIMEOUT:-90s}
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - internal
      - traefik
    labels:
      traefik.enable: "true"
      traefik.docker.network: n8n_default
      traefik.http.routers.inversiones.rule: Host(`${BACKEND_HOST:?BACKEND_HOST is required}`)
      traefik.http.routers.inversiones.entrypoints: web,websecure
      traefik.http.routers.inversiones.tls: "true"
      traefik.http.routers.inversiones.tls.certresolver: mytlschallenge
      traefik.http.services.inversiones.loadbalancer.server.port: "8080"
      # Mirrors the header middleware the n8n container already uses on this Traefik.
      traefik.http.routers.inversiones.middlewares: inversiones@docker
      traefik.http.middlewares.inversiones.headers.contentTypeNosniff: "true"
      traefik.http.middlewares.inversiones.headers.browserXSSFilter: "true"
      traefik.http.middlewares.inversiones.headers.forceSTSHeader: "true"
      traefik.http.middlewares.inversiones.headers.STSSeconds: "31536000"
      traefik.http.middlewares.inversiones.headers.STSIncludeSubdomains: "true"

  postgres:
    image: postgres:17-alpine
    container_name: inversiones-postgres
    restart: unless-stopped
    mem_limit: 512m
    memswap_limit: 512m
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-inversiones}
      POSTGRES_USER: ${POSTGRES_USER:-inversiones}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
    # No `ports:` block, deliberately. The dev compose publishes 5432 so pgAdmin can reach it from
    # the host; doing that here would expose the database to the internet. Only the backend needs
    # it, and it reaches it over the internal network by service name.
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-inversiones} -d ${POSTGRES_DB:-inversiones}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s
    networks:
      - internal

volumes:
  pgdata:

networks:
  internal:
    driver: bridge
  traefik:
    # Created and owned by the n8n stack. Referenced, never created, so `compose down` here can
    # never take Traefik's network out from under n8n.
    name: n8n_default
    external: true
```

- [ ] **Step 4: Extend `deploy/.env.template`**

Append:

```bash
# ---------------------------------------------------------------------------
# Production (deploy/docker-compose.prod.yml). On the VPS this file lives at
# /docker/inversiones/.env with permissions 600. It is git-ignored; this
# template is what is tracked.
# ---------------------------------------------------------------------------

# The hostname Traefik routes to this backend. *.srv1449561.hstgr.cloud is a DNS wildcard
# pointing at the VPS, so any subdomain resolves with no DNS change.
BACKEND_HOST=api.srv1449561.hstgr.cloud

# The deployed frontend's exact origin, no wildcard. Several are comma-separated.
SECURITY_ALLOWED_ORIGINS=https://inversiones.vercel.app

# Generate with: openssl rand -base64 48
# Must be at least 32 bytes. The committed local default is rejected outside the local profile.
JWT_SECRET=

# Generate with: openssl rand -base64 32
POSTGRES_PASSWORD=

# n8n is reachable inside the VPS over its own container name, so this stays off the public
# internet. Override only if n8n moves.
N8N_BASE_URL=http://n8n:5678/webhook/inv
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./deploy/__tests__/compose-prod.sh
```

Expected: all 8 checks `ok`.

- [ ] **Step 6: Prove the memory-limit check has teeth**

Comment out `mem_limit: 1g` under `backend`, re-run the script, confirm `the BACKEND declares mem_limit` FAILS, then restore it. This is the check that protects n8n; a version of it that passes without the limit present would be worse than no check at all.

- [ ] **Step 7: Verify the stack actually starts, locally**

```bash
cd deploy
POSTGRES_PASSWORD=local_prod_test JWT_SECRET="$(openssl rand -base64 48)" \
  BACKEND_HOST=localhost SECURITY_ALLOWED_ORIGINS=http://localhost:5173 \
  docker compose -f docker-compose.prod.yml --profile '' up -d postgres
docker compose -f docker-compose.prod.yml ps
```

Expected: `inversiones-postgres` reaches `healthy`. The `backend` service is not started here because the `traefik` external network does not exist on this machine — that is correct and expected; Task 6 covers the VPS run. Tear down with:

```bash
docker compose -f docker-compose.prod.yml down -v
```

- [ ] **Step 8: Commit**

```bash
git add deploy/docker-compose.prod.yml deploy/.env.template deploy/__tests__/compose-prod.sh
git commit -m "feat(deploy): add the production compose stack with memory limits

The VPS has 7.8 GB of RAM, no swap, and none of its seven existing
containers declares a memory limit. The backend image sets
MaxRAMPercentage=75, which sizes the heap from the container's cgroup
limit, so deploying without one would have the JVM target 75% of the
host and the kernel kill a process to get it back — most likely n8n,
which is this application's only source of market data.

Postgres publishes no host port, unlike the dev compose, which publishes
it so pgAdmin can connect. The Traefik network is referenced as external
so tearing this stack down can never remove n8n's network.

The test asserts each of those properties, because every one of them is
silently fine in development and expensive in production."
```

---

## Task 4: The Jenkinsfile

**Files:**
- Create: `Jenkinsfile`
- Create: `jenkins/lint-jenkinsfile.sh`
- Modify: `backend/applications/app-service/build.gradle` (the `acceptanceTest` task, to emit a Cucumber HTML report that does not exist today)

**Interfaces:**
- Consumes: `deploy/docker-compose.prod.yml` (Task 3), `frontend/vercel.json` (Task 2), `scripts/smoke.sh` (already exists, takes a base URL as `$1`).
- Produces: the `DEPLOY_TARGET` parameter contract (`none` | `local` | `vps`) that Task 5's JCasC job definition sets.

**Why this task exists.** The pipeline is the deliverable the spec names, and it is also the place where a stage that runs nothing looks exactly like a stage that passes. Three specific traps this project has already hit: Gradle reporting `BUILD SUCCESSFUL` from up-to-date tasks, vitest printing `Tests 90 passed` while a whole file failed to compile, and a smoke test against a URL that never came up.

- [ ] **Step 1: Create the lint script**

Create `jenkins/lint-jenkinsfile.sh`, executable:

```bash
#!/usr/bin/env bash
# Validates the declarative Jenkinsfile against a running Jenkins' linter. Without this, a syntax
# error in the pipeline is discovered by a failed build rather than before the commit.
set -euo pipefail

JENKINS_URL="${JENKINS_URL:-http://localhost:8080}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if ! curl -sf "$JENKINS_URL/login" >/dev/null 2>&1; then
  echo "Jenkins is not reachable at $JENKINS_URL — start it with:"
  echo "  docker compose -f jenkins/docker-compose.yml up -d"
  exit 2
fi

CRUMB="$(curl -sf "$JENKINS_URL/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)" || true)"
if [ -n "$CRUMB" ]; then
  curl -sf -X POST -H "$CRUMB" -F "jenkinsfile=<${ROOT}/Jenkinsfile" "$JENKINS_URL/pipeline-model-converter/validate"
else
  curl -sf -X POST -F "jenkinsfile=<${ROOT}/Jenkinsfile" "$JENKINS_URL/pipeline-model-converter/validate"
fi
```

`chmod +x jenkins/lint-jenkinsfile.sh`

- [ ] **Step 2a: Make the Cucumber HTML report actually exist**

The spec's §7.3 lists a Cucumber HTML report among the published artifacts, but **none is generated today.** The acceptance suite runs through `cucumber-junit-platform-engine`, so its results come out only as JUnit XML. Publishing a directory that is never written would give the pipeline a stage that reports nothing while looking like it reported something — the exact failure this project has hit repeatedly.

In `backend/applications/app-service/build.gradle`, inside the existing `tasks.register('acceptanceTest', Test)` block, add:

```groovy
    // Cucumber's JUnit Platform engine emits no HTML on its own. Without this, the pipeline's
    // Cucumber report directory is never written, and publishing it would show an empty artifact
    // that looks like a passing report.
    systemProperty 'cucumber.plugin',
            "html:${layout.buildDirectory.get().asFile}/reports/cucumber/report.html"
```

Verify it writes the file:

```bash
cd backend
./gradlew --no-daemon --rerun-tasks :app-service:acceptanceTest
ls -lh applications/app-service/build/reports/cucumber/report.html
```

Expected: the file exists and is non-trivial in size. If `acceptanceTest` needs Docker and Docker is down, start it first — the 11 scenarios run against Testcontainers.

- [ ] **Step 2: Create the `Jenkinsfile`**

```groovy
// Declarative pipeline, identical on the Mac and on the VPS. The deploy target is a parameter
// rather than a branch condition so the same pipeline can be exercised end to end locally before
// any credential for the real targets exists.
pipeline {
  agent any

  parameters {
    choice(
      name: 'DEPLOY_TARGET',
      choices: ['none', 'local', 'vps'],
      description: 'none: build and test only. local: compose up on this host. vps: remote Docker context over SSH.'
    )
  }

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 60, unit: 'MINUTES')
  }

  environment {
    IMAGE_TAG = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(12) : 'dev'}"
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        sh 'git --no-pager log --oneline -1'
      }
    }

    stage('Backend: Build & Test') {
      steps {
        dir('backend') {
          // --rerun-tasks is not a precaution, it is the point: without it Gradle reports
          // BUILD SUCCESSFUL from up-to-date tasks and the tests never run, which is
          // indistinguishable from a pass. `build` already depends on validateStructure
          // (compileJava.dependsOn) and on jacocoMergedCoverageVerification (check.dependsOn),
          // so the 80% gate and the clean-architecture check are both enforced here.
          sh './gradlew --no-daemon --rerun-tasks build'
        }
      }
      post {
        always {
          junit testResults: 'backend/**/build/test-results/test/*.xml', allowEmptyResults: false
        }
      }
    }

    stage('Frontend: Build & Test') {
      steps {
        dir('frontend') {
          sh 'npm ci'
          sh 'npm run typecheck'
          sh 'npm run lint'
          // Read the machine-readable report rather than parsing stdout: vitest emits ANSI
          // escapes inside the phrase "Test Files", and a file that fails to COMPILE prints
          // "Test Files 1 failed" while the Tests line still reads all-passed.
          sh 'npx vitest run --coverage --reporter=json --outputFile=.vitest/report.json'
          sh '''
            python3 - <<'PY'
import json, sys
r = json.load(open('.vitest/report.json'))
print(f"suites={r.get('numTotalTestSuites')} failedSuites={r.get('numFailedTestSuites')} "
      f"tests={r.get('numTotalTests')} failed={r.get('numFailedTests')}")
if not r.get('success') or r.get('numFailedTestSuites') or r.get('numFailedTests'):
    sys.exit('vitest reported failures')
PY
          '''
          sh 'npm run build'
        }
      }
    }

    stage('Docker Build') {
      steps {
        sh 'docker build -t inversiones-backend:${IMAGE_TAG} -f backend/deployment/Dockerfile backend'
        sh 'docker image inspect inversiones-backend:${IMAGE_TAG} --format "built {{.Id}} size {{.Size}}"'
      }
    }

    stage('Acceptance Tests') {
      steps {
        sh 'docker compose -f deploy/docker-compose.dev.yml up -d --wait'
        dir('backend') {
          sh './gradlew --no-daemon --rerun-tasks :app-service:acceptanceTest'
        }
        dir('frontend') {
          sh 'npx playwright install --with-deps chromium webkit'
          sh 'npm run e2e'
        }
      }
      post {
        always {
          sh 'docker compose -f deploy/docker-compose.dev.yml down -v || true'
        }
      }
    }

    stage('Publish Reports') {
      steps {
        publishHTML(target: [
          reportDir: 'backend/build/reports/jacoco/jacocoMergedReport/html',
          reportFiles: 'index.html', reportName: 'JaCoCo (merged)',
          keepAll: true, alwaysLinkToLastBuild: true, allowMissing: true
        ])
        publishHTML(target: [
          reportDir: 'backend/applications/app-service/build/reports/cucumber',
          reportFiles: 'report.html', reportName: 'Cucumber',
          // allowMissing stays false here on purpose: the point of Step 2b below is that this
          // report now exists, and a silently-absent report is the failure mode this project
          // keeps hitting. If it goes missing, say so loudly.
          keepAll: true, alwaysLinkToLastBuild: true, allowMissing: false
        ])
        publishHTML(target: [
          reportDir: 'frontend/playwright-report',
          reportFiles: 'index.html', reportName: 'Playwright',
          keepAll: true, alwaysLinkToLastBuild: true, allowMissing: true
        ])
      }
    }

    stage('Deploy Backend') {
      when { expression { params.DEPLOY_TARGET != 'none' } }
      steps {
        script {
          if (params.DEPLOY_TARGET == 'local') {
            withCredentials([
              string(credentialsId: 'inversiones-jwt-secret', variable: 'JWT_SECRET'),
              string(credentialsId: 'inversiones-postgres-password', variable: 'POSTGRES_PASSWORD')
            ]) {
              // No external Traefik network exists on a dev host, so only Postgres comes up and
              // the backend runs from the dev compose. This path validates the pipeline, not the
              // production topology.
              sh '''
                docker compose -f deploy/docker-compose.dev.yml up -d --wait
                docker compose -f deploy/docker-compose.dev.yml ps
              '''
            }
          } else {
            withCredentials([
              string(credentialsId: 'inversiones-jwt-secret', variable: 'JWT_SECRET'),
              string(credentialsId: 'inversiones-postgres-password', variable: 'POSTGRES_PASSWORD'),
              string(credentialsId: 'inversiones-backend-host', variable: 'BACKEND_HOST'),
              string(credentialsId: 'inversiones-allowed-origins', variable: 'SECURITY_ALLOWED_ORIGINS')
            ]) {
              // DOCKER_HOST points at the VPS over SSH; the compose file is evaluated locally and
              // the containers run there.
              sh '''
                export DOCKER_HOST=ssh://root@148.230.88.179
                docker compose -f deploy/docker-compose.prod.yml up -d --build --wait
                docker compose -f deploy/docker-compose.prod.yml ps
              '''
            }
          }
        }
      }
    }

    stage('Deploy Frontend') {
      when { expression { params.DEPLOY_TARGET == 'vps' } }
      steps {
        withCredentials([string(credentialsId: 'vercel-token', variable: 'VERCEL_TOKEN')]) {
          dir('frontend') {
            sh 'npx vercel deploy --prod --yes --token=$VERCEL_TOKEN'
          }
        }
      }
    }

    stage('Smoke Test') {
      when { expression { params.DEPLOY_TARGET != 'none' } }
      steps {
        script {
          // scripts/smoke.sh takes the base URL as $1 and exits non-zero with a distinct code per
          // failed step, including a check that an unauthenticated call is rejected.
          def base = params.DEPLOY_TARGET == 'local'
            ? 'http://localhost:8080'
            : 'https://api.srv1449561.hstgr.cloud'
          sh "./scripts/smoke.sh ${base}"
        }
      }
    }
  }

  post {
    always {
      sh 'docker compose -f deploy/docker-compose.dev.yml down -v || true'
    }
  }
}
```

- [ ] **Step 3: Verify each guard actually guards, by inspection against a checklist**

Run this and confirm every line prints `ok`:

```bash
cd "$(git rev-parse --show-toplevel)"
grep -q 'rerun-tasks build' Jenkinsfile && echo "ok  gradle build forces a real run" || echo "FAIL"
grep -q 'numFailedTestSuites' Jenkinsfile && echo "ok  vitest failures read from the JSON report" || echo "FAIL"
grep -q 'allowEmptyResults: false' Jenkinsfile && echo "ok  an empty JUnit result set fails the build" || echo "FAIL"
grep -q 'scripts/smoke.sh' Jenkinsfile && echo "ok  the smoke stage calls the real script" || echo "FAIL"
grep -q "DEPLOY_TARGET == 'vps'" Jenkinsfile && echo "ok  the frontend deploys only on the vps target" || echo "FAIL"
grep -q 'down -v || true' Jenkinsfile && echo "ok  the dev stack is torn down even on failure" || echo "FAIL"
```

`allowEmptyResults: false` matters more than it looks: with `true`, a build whose tests never ran publishes zero results and still goes green.

- [ ] **Step 4: Commit**

```bash
git add Jenkinsfile jenkins/lint-jenkinsfile.sh backend/applications/app-service/build.gradle
git commit -m "feat(ci): add the declarative pipeline with a parameterised deploy target

Nine stages, with DEPLOY_TARGET as a parameter rather than a branch
condition so the whole pipeline can be exercised locally before any
production credential exists.

Three guards are deliberate rather than stylistic. Gradle runs with
--rerun-tasks because BUILD SUCCESSFUL from up-to-date tasks is
indistinguishable from a pass. Vitest failures are read from its JSON
report because a file that fails to compile prints 'Test Files 1 failed'
while the Tests line still reads all-passed. junit is configured with
allowEmptyResults: false because a build whose tests never ran would
otherwise publish nothing and go green."
```

---

## Task 5: Jenkins on the Mac, as code

**Files:**
- Create: `jenkins/docker-compose.yml`
- Create: `jenkins/plugins.txt`
- Create: `jenkins/casc/jenkins.yaml`
- Create: `jenkins/README.md`

**Interfaces:**
- Consumes: the `DEPLOY_TARGET` parameter from Task 4's `Jenkinsfile`.
- Produces: a reachable Jenkins at `http://localhost:8080` with a pipeline job named `inversiones`, which Task 6's VPS phase reuses unchanged.

**Why this task exists.** The spec requires the same Jenkins configuration to reproduce on the Mac and on the VPS. A hand-clicked Jenkins reproduces nowhere, and the pipeline from Task 4 is unproven until something runs it.

- [ ] **Step 1: Create `jenkins/plugins.txt`**

```text
configuration-as-code
workflow-aggregator
git
github
github-branch-source
docker-workflow
pipeline-stage-view
junit
jacoco
htmlpublisher
credentials-binding
timestamps
ws-cleanup
```

- [ ] **Step 2: Create `jenkins/docker-compose.yml`**

```yaml
# Jenkins for this project, configured entirely as code so the same setup reproduces on the Mac
# and on the VPS. Nothing here is clicked.
#
# The Docker socket is mounted because the pipeline builds images and runs compose stacks. That
# grants this container root-equivalent control of the host's Docker, which is acceptable for a
# single-developer CI host and would not be for a shared one.

services:
  jenkins:
    image: jenkins/jenkins:lts-jdk21
    container_name: inversiones-jenkins
    restart: unless-stopped
    # Jenkins itself is modest; the builds it spawns are not, and they run as sibling containers
    # with their own limits rather than inside this one.
    mem_limit: 2g
    environment:
      JAVA_OPTS: -Djenkins.install.runSetupWizard=false -Dhudson.model.DirectoryBrowserSupport.CSP=
      CASC_JENKINS_CONFIG: /var/jenkins_conf/jenkins.yaml
      JENKINS_ADMIN_ID: ${JENKINS_ADMIN_ID:-admin}
      JENKINS_ADMIN_PASSWORD: ${JENKINS_ADMIN_PASSWORD:?set JENKINS_ADMIN_PASSWORD in jenkins/.env}
      INVERSIONES_REPO_URL: ${INVERSIONES_REPO_URL:-https://github.com/esneider1504/inversiones.git}
      INVERSIONES_BRANCH: ${INVERSIONES_BRANCH:-main}
    ports:
      - "${JENKINS_PORT:-8080}:8080"
      - "50000:50000"
    volumes:
      - jenkins_home:/var/jenkins_home
      - ./casc:/var/jenkins_conf:ro
      - ./plugins.txt:/usr/share/jenkins/ref/plugins.txt:ro
      - /var/run/docker.sock:/var/run/docker.sock
    user: root

volumes:
  jenkins_home:
```

Note the port: the backend also uses 8080 on this machine. `JENKINS_PORT` exists so they can coexist — set it to `8090` in `jenkins/.env` when running both.

- [ ] **Step 3: Create `jenkins/casc/jenkins.yaml`**

```yaml
# Jenkins Configuration as Code. Everything Jenkins needs to run this project's pipeline, with no
# manual setup step. Credentials are read from the environment, never written here.
jenkins:
  systemMessage: "Inversiones CI — configured as code from jenkins/casc/jenkins.yaml"
  numExecutors: 2
  mode: NORMAL
  securityRealm:
    local:
      allowsSignup: false
      users:
        - id: "${JENKINS_ADMIN_ID}"
          password: "${JENKINS_ADMIN_PASSWORD}"
  authorizationStrategy:
    loggedInAuthenticationStrategy:
      allowAnonymousRead: false
  crumbIssuer:
    standard:
      excludeClientIPFromCrumb: false

unclassified:
  location:
    url: "http://localhost:${JENKINS_PORT:-8080}/"

credentials:
  system:
    domainCredentials:
      - credentials:
          - string:
              scope: GLOBAL
              id: "inversiones-jwt-secret"
              secret: "${JWT_SECRET_CI:-ci-placeholder-secret-at-least-32-bytes!}"
              description: "JWT signing secret used by the deploy stages"
          - string:
              scope: GLOBAL
              id: "inversiones-postgres-password"
              secret: "${POSTGRES_PASSWORD_CI:-ci_placeholder_password}"
              description: "Postgres password used by the deploy stages"
          # The three below are only read by the `vps` target. They are declared here with
          # placeholders because a withCredentials block fails the build when its credential id
          # does not exist at all — so without these, choosing `vps` would fail on a missing
          # credential rather than on the real thing it is missing, which is the account behind it.
          - string:
              scope: GLOBAL
              id: "inversiones-backend-host"
              secret: "${BACKEND_HOST_CI:-api.srv1449561.hstgr.cloud}"
              description: "Hostname Traefik routes to the backend"
          - string:
              scope: GLOBAL
              id: "inversiones-allowed-origins"
              secret: "${ALLOWED_ORIGINS_CI:-https://replace-me.vercel.app}"
              description: "Exact origin of the deployed frontend; no wildcard"
          - string:
              scope: GLOBAL
              id: "vercel-token"
              secret: "${VERCEL_TOKEN_CI:-replace-me-no-vercel-account-yet}"
              description: "Vercel deploy token. No account exists yet; see docs/runbook-vps.md"

jobs:
  - script: |
      pipelineJob('inversiones') {
        description('Build, test and deploy the inversiones project. Deploy target is a parameter.')
        parameters {
          choiceParam('DEPLOY_TARGET', ['none', 'local', 'vps'],
            'none: build and test only. local: compose up here. vps: remote Docker over SSH.')
        }
        definition {
          cpsScm {
            scm {
              git {
                remote { url(System.getenv('INVERSIONES_REPO_URL')) }
                branch(System.getenv('INVERSIONES_BRANCH'))
              }
            }
            scriptPath('Jenkinsfile')
            lightweight(true)
          }
        }
        // No public IP on a laptop, so webhooks cannot arrive. The VPS phase replaces this with
        // a GitHub webhook; see docs/runbook-vps.md.
        triggers {
          scm('H/15 * * * *')
        }
      }
```

- [ ] **Step 4: Create `jenkins/README.md`**

```markdown
# Jenkins for inversiones

Configured as code. Nothing here is clicked; `casc/jenkins.yaml` is the whole configuration.

## Start

    cp .env.template .env        # then set JENKINS_ADMIN_PASSWORD
    docker compose -f jenkins/docker-compose.yml up -d
    open http://localhost:8090   # JENKINS_PORT, kept off 8080 so the backend can use it

## Run the pipeline

The job is named `inversiones`. Build with `DEPLOY_TARGET=none` first — it builds and tests
without deploying anything. Then `local`, which also brings up the dev compose stack and runs
the smoke script against it.

`vps` is the real deploy and needs credentials that do not exist yet; see
`docs/runbook-vps.md`.

## Validate a Jenkinsfile change before committing

    JENKINS_URL=http://localhost:8090 ./jenkins/lint-jenkinsfile.sh

## Why the Docker socket is mounted

The pipeline builds images and runs compose stacks, so it needs the host's Docker. That is
root-equivalent access to this machine's Docker daemon — fine for a single-developer CI host,
not fine for a shared one.
```

Create `jenkins/.env.template`:

```bash
# Copy to jenkins/.env (git-ignored) and fill in.
JENKINS_ADMIN_ID=admin
JENKINS_ADMIN_PASSWORD=
# 8080 is taken by the backend on this machine.
JENKINS_PORT=8090
INVERSIONES_REPO_URL=https://github.com/esneider1504/inversiones.git
INVERSIONES_BRANCH=main
```

- [ ] **Step 5: Add `jenkins/.env` to `.gitignore`**

Append to `.gitignore`:

```
jenkins/.env
```

- [ ] **Step 6: Start Jenkins and confirm the job exists**

```bash
cd "$(git rev-parse --show-toplevel)"
cp jenkins/.env.template jenkins/.env
# set JENKINS_ADMIN_PASSWORD in jenkins/.env, then:
docker compose -f jenkins/docker-compose.yml --env-file jenkins/.env up -d
sleep 90
curl -s -o /dev/null -w "jenkins: HTTP %{http_code}\n" http://localhost:8090/login
```

Expected: `HTTP 200`. Then confirm the job was created by configuration rather than by hand:

```bash
curl -s -u "admin:$(grep JENKINS_ADMIN_PASSWORD jenkins/.env | cut -d= -f2)" \
  http://localhost:8090/api/json?tree=jobs[name] | python3 -m json.tool
```

Expected: a job named `inversiones`.

- [ ] **Step 7: Lint the Jenkinsfile against the running Jenkins**

```bash
JENKINS_URL=http://localhost:8090 ./jenkins/lint-jenkinsfile.sh
```

Expected: `Jenkinsfile successfully validated.`

- [ ] **Step 8: Run the pipeline with `DEPLOY_TARGET=none`**

Trigger it from the UI or:

```bash
curl -s -X POST -u "admin:$PASS" \
  "http://localhost:8090/job/inversiones/buildWithParameters?DEPLOY_TARGET=none"
```

Expected: the build reaches `Publish Reports` and succeeds. The `Deploy Backend`, `Deploy Frontend` and `Smoke Test` stages are skipped by their `when` guards.

- [ ] **Step 9: Run the pipeline with `DEPLOY_TARGET=local` — the real validation**

```bash
curl -s -X POST -u "admin:$PASS" \
  "http://localhost:8090/job/inversiones/buildWithParameters?DEPLOY_TARGET=local"
```

Expected: all stages except `Deploy Frontend` run, and `Smoke Test` passes 4/4 against `http://localhost:8080`.

**This step is the one that proves the plan.** If it goes green, the pipeline works end to end and only credentials separate it from a real deploy.

- [ ] **Step 10: Prove a stage fails when its subject is broken**

Break one thing, run `DEPLOY_TARGET=none`, confirm the build goes RED, then restore it. Pick one: add a failing assertion to any backend test, or a type error to any `.ts` file. A pipeline that has never failed is a pipeline whose guards are unproven.

Record in the task report which one you broke and which stage caught it.

- [ ] **Step 11: Commit**

```bash
git add jenkins/ .gitignore
git commit -m "feat(ci): run Jenkins from configuration as code

The spec requires the same Jenkins configuration to reproduce on the Mac
and on the VPS, and a hand-clicked Jenkins reproduces nowhere. The job,
its DEPLOY_TARGET parameter, the credential bindings and the SCM polling
trigger all come from casc/jenkins.yaml.

Jenkins listens on 8090 because the backend uses 8080 on this machine.
Polling replaces a webhook here: a laptop has no public IP for GitHub to
reach, and the VPS phase swaps it for a real webhook."
```

---

## Task 6: Backups and the VPS runbook

**Files:**
- Create: `deploy/backup/pg_dump_daily.sh`
- Create: `docs/runbook-vps.md`

**Interfaces:**
- Consumes: `deploy/docker-compose.prod.yml` (Task 3), the Jenkins setup (Task 5).
- Produces: nothing other tasks consume. This is the last task.

**Why this task exists.** The spec requires a daily `pg_dump` by cron, and the steps that need credentials or touch infrastructure outside this repository have to be written down rather than performed. Everything in the runbook is a step a person takes deliberately, once.

- [ ] **Step 1: Create `deploy/backup/pg_dump_daily.sh`**

```bash
#!/usr/bin/env bash
# Daily logical backup of the production database, run by cron on the VPS.
#
# Writes a compressed dump, keeps 14 of them, and exits non-zero if the dump is suspiciously
# small — an empty or truncated dump that is kept and rotated is worse than no backup, because it
# looks like one until the day it is needed.
set -euo pipefail

CONTAINER="${CONTAINER:-inversiones-postgres}"
DB="${POSTGRES_DB:-inversiones}"
USER="${POSTGRES_USER:-inversiones}"
DEST="${DEST:-/var/backups/inversiones}"
KEEP="${KEEP:-14}"
MIN_BYTES="${MIN_BYTES:-10240}"

mkdir -p "$DEST"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
out="$DEST/inversiones-$stamp.sql.gz"

docker exec "$CONTAINER" pg_dump -U "$USER" -d "$DB" --clean --if-exists | gzip > "$out"

size="$(stat -c %s "$out" 2>/dev/null || stat -f %z "$out")"
if [ "$size" -lt "$MIN_BYTES" ]; then
  echo "backup $out is only $size bytes (< $MIN_BYTES) — treating as failed" >&2
  mv "$out" "$out.suspect"
  exit 1
fi

# Rotate: keep the newest $KEEP, delete the rest.
ls -1t "$DEST"/inversiones-*.sql.gz 2>/dev/null | tail -n "+$((KEEP + 1))" | xargs -r rm --

echo "backup ok: $out ($size bytes); $(ls -1 "$DEST"/inversiones-*.sql.gz | wc -l) kept"
```

`chmod +x deploy/backup/pg_dump_daily.sh`

- [ ] **Step 2: Test the backup script against the dev database**

```bash
cd "$(git rev-parse --show-toplevel)"
docker compose -f deploy/docker-compose.dev.yml up -d postgres --wait
CONTAINER=inversiones-postgres DEST=/tmp/bk-test ./deploy/backup/pg_dump_daily.sh
ls -lh /tmp/bk-test/
```

Expected: one `.sql.gz` well over 10 KB, and `backup ok:` on stdout.

- [ ] **Step 3: Prove the size guard works**

```bash
MIN_BYTES=999999999 CONTAINER=inversiones-postgres DEST=/tmp/bk-test2 \
  ./deploy/backup/pg_dump_daily.sh; echo "exit=$?"
ls /tmp/bk-test2/
```

Expected: `exit=1`, and the file renamed to `*.suspect`. This is the guard that distinguishes a real backup from a file that merely exists — without it, a truncated dump rotates into the keep set and looks healthy until restore day.

Clean up: `rm -rf /tmp/bk-test /tmp/bk-test2`

- [ ] **Step 4: Create `docs/runbook-vps.md`**

```markdown
# VPS and Vercel runbook

The steps that need credentials or touch infrastructure outside this repository. Each is done
deliberately, once. Everything else is in the pipeline.

## Verified facts about the VPS

Checked over SSH on 2026-10-28, not assumed:

| Fact | Value |
|---|---|
| Host | `148.230.88.179`, hostname `srv1449561` |
| DNS | `*.srv1449561.hstgr.cloud` is a **wildcard** to that IP — no DNS change needed |
| Traefik | container `n8n-traefik-1`, network **`n8n_default`**, owns :80 and :443 |
| Entrypoints | `web` (:80, redirects to websecure), `websecure` (:443) |
| Cert resolver | **`mytlschallenge`**, TLS-ALPN-01 |
| Traefik default | `exposedbydefault=false` — `traefik.enable=true` is required |
| Memory | 7.8 GB total, 5.2 GB available, **no swap** |
| Existing limits | none of the seven running containers declares one |
| CPUs | 2 |
| Disk | 96 GB, 80 GB free |
| Stacks | `/docker/n8n`, `/docker/social-autopost`. This one goes in `/docker/inversiones` |
| Free host ports | 8080 and 5432 are unused |

## 1. Vercel, first time

No account exists yet.

1. Create an account at vercel.com and a project pointing at this repository.
2. Set the root directory to `frontend`. `vercel.json` supplies the build command and output
   directory, so leave those at their detected values.
3. Add the environment variable, for both Production and Preview:

       VITE_API_BASE_URL = https://api.srv1449561.hstgr.cloud

4. Create a token under Account Settings → Tokens.
5. Note the production domain Vercel assigns — it goes into `SECURITY_ALLOWED_ORIGINS` in step 2,
   and the backend will reject the frontend until it does.

## 2. The VPS, first deploy

```bash
ssh root@148.230.88.179
mkdir -p /docker/inversiones && cd /docker/inversiones
# Copy deploy/.env.template here as .env and fill in every value.
chmod 600 .env
```

Generate the two secrets on the VPS, not locally:

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 32   # POSTGRES_PASSWORD
```

Confirm the Traefik network before deploying — the compose file references it as external, so a
wrong name fails the deploy rather than creating a stray network:

```bash
docker network ls --filter name=n8n_default
```

Then deploy from the Mac, with the compose file evaluated locally and the containers run there:

```bash
export DOCKER_HOST=ssh://root@148.230.88.179
docker compose -f deploy/docker-compose.prod.yml up -d --build --wait
```

Verify, in this order:

```bash
docker compose -f deploy/docker-compose.prod.yml ps          # both healthy
curl -s https://api.srv1449561.hstgr.cloud/actuator/health   # {"status":"UP"}
./scripts/smoke.sh https://api.srv1449561.hstgr.cloud        # 4/4
```

The certificate is issued on the first HTTPS request and takes a few seconds. If it fails, check
`docker logs n8n-traefik-1` for the ACME error rather than retrying blindly — repeated failures
count against Let's Encrypt's rate limit.

**Confirm the memory limits took effect.** This is the check that protects n8n:

```bash
docker inspect inversiones-backend --format 'mem_limit={{.HostConfig.Memory}}'
docker exec inversiones-backend java -XX:+PrintFlagsFinal -version | grep -i maxheapsize
```

Expected: `mem_limit=1073741824`, and a `MaxHeapSize` near 750 MB — **not** near 5.9 GB. A heap
sized from the host rather than the container means the limit is not being applied, and with no
swap on this machine the kernel will eventually kill a process to reclaim it.

## 3. Jenkins on the VPS

Same `jenkins/docker-compose.yml`, with two changes:

- Route it through Traefik with its own `Host(...)` label rather than publishing a port.
- Replace the SCM polling trigger in `casc/jenkins.yaml` with a GitHub webhook: add
  `https://<jenkins-host>/github-webhook/` under the repository's Settings → Webhooks, content
  type `application/json`, push events only.

Polling exists only because a laptop has no public IP for GitHub to reach.

## 4. Daily backup

```bash
scp deploy/backup/pg_dump_daily.sh root@148.230.88.179:/usr/local/bin/
ssh root@148.230.88.179 'chmod +x /usr/local/bin/pg_dump_daily.sh'
ssh root@148.230.88.179 'crontab -l 2>/dev/null; echo "15 3 * * * /usr/local/bin/pg_dump_daily.sh >> /var/log/inversiones-backup.log 2>&1"' | ssh root@148.230.88.179 'crontab -'
```

Then prove a restore works, because a backup nobody has restored is a hypothesis:

```bash
ssh root@148.230.88.179 '
  gunzip -c /var/backups/inversiones/$(ls -1t /var/backups/inversiones | head -1) \
    | docker exec -i inversiones-postgres psql -U inversiones -d postgres \
      -c "CREATE DATABASE restore_test;" -d restore_test
  docker exec inversiones-postgres psql -U inversiones -d restore_test -c "select count(*) from transactions;"
  docker exec inversiones-postgres psql -U inversiones -d postgres -c "DROP DATABASE restore_test;"
'
```

## Known limitations, carried from earlier plans

- Two of the six signal sources (`news`, `analyst`) return no data from n8n today, so several
  scores arrive null. Workflow state, not an API fault.
- `/inv/earnings` (WF-13) returns `[]`.
- A degraded upstream reaches clients as HTTP 404 "Could not resolve ticker", which names the
  wrong cause; 503 would be honest. Recorded in `docs/superpowers/decisions.md`.
- Mutation testing runs but gates nothing, and its 208 `NO_COVERAGE` mutations suggest PIT is not
  running over the class set JaCoCo measures. Diagnose that before setting any threshold.
```

- [ ] **Step 5: Commit**

```bash
git add deploy/backup/pg_dump_daily.sh docs/runbook-vps.md
git commit -m "feat(deploy): add the daily backup script and the VPS runbook

The backup exits non-zero and renames the file when a dump comes back
suspiciously small: a truncated dump that rotates into the keep set looks
like a backup until the day it is needed.

The runbook holds the steps that need credentials or touch infrastructure
outside this repository, and records the VPS facts that were verified over
SSH rather than assumed — the Traefik network name, the cert resolver, the
DNS wildcard, and that the machine has no swap and no container memory
limits. It ends by checking that the JVM heap was sized from the container
rather than the host, which is the check that keeps this deploy from
getting n8n killed."
```

---

## Execution Notes

**Task order matters only in two places.** Task 1 must precede Task 3 (the compose file sets the variables Task 1 defines), and Task 4 must precede Task 5 (JCasC references the `Jenkinsfile`'s parameter). Tasks 2 and 6 are independent.

**What "done" means for this plan.** Step 9 of Task 5 — the pipeline running green end to end with `DEPLOY_TARGET=local`, smoke test included. The `vps` path is written, linted and reviewed, but it cannot be executed until a Vercel account and the VPS `.env` exist, and those are deliberate human steps in the runbook.

**The one risk worth restating.** The VPS has no swap and seven containers with no memory limits. If the memory limits in Task 3 are dropped, weakened, or silently not applied, the JVM will size its heap from the host and the kernel will kill something. Task 3's test and the runbook's post-deploy check both exist for that single failure, and neither should be removed because it looks redundant — they verify different things, the file and the running container.
