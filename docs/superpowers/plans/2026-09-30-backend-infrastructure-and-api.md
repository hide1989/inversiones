# Backend Infrastructure and API Implementation Plan (Plan 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement every port the domain core defined, expose the use cases over a REST API, and make the whole backend run locally with one `docker compose up` — no cloud account required.

**Architecture:** The domain core from Plan 1 is untouched except for one Critical fix (Task 1). Everything added here lives in `infrastructure/`: a `jpa-repository` driven adapter over PostgreSQL with Flyway migrations, a `rest-consumer` driven adapter against the existing n8n webhooks, a `security-jwt` adapter, and a `restmvc` entry point. `applications/app-service` does the wiring. Nothing in `domain/` learns that any of this exists.

**Tech Stack:** Java 25, Spring Boot 4.1.1, Spring Data JPA / Hibernate, PostgreSQL 17, Flyway, Resilience4j, Caffeine, jjwt, springdoc-openapi, Testcontainers, MockWebServer, WireMock, Cucumber JVM 7.

**Spec:** `spec.md` — sections 4.1, 4.6, 4.7, 4.8, 6.1, 6.2; phases 5–9 of section 8.
**Predecessor:** `docs/superpowers/plans/2026-09-29-backend-domain-core.md` (complete: 12 tasks, 116 tests, merged coverage 97.3%).

## Local-first priority

Everything in this plan runs on this machine with Docker and nothing else. No Vercel account, no VPS access, no GitHub webhook, no cloud credentials. Deployment to Vercel and the Hostinger VPS, and Jenkins running anywhere but locally, are **Plan 4** and are deliberately absent here.

Concretely, by the end of this plan:
```bash
docker compose -f deploy/docker-compose.dev.yml up -d   # postgres + backend
curl localhost:8080/actuator/health                     # {"status":"UP"}
curl -X POST localhost:8080/api/v1/auth/login -d '...'  # returns a JWT
cd backend && ./gradlew test                            # unit + integration, Testcontainers
cd backend && ./gradlew acceptanceTest                  # Cucumber against a real Postgres + WireMock
```

## Global Constraints

Everything in Plan 1's Global Constraints still binds, plus:

- Java version **25**; root package **`co.com.david.inversiones`**; Spring Boot **4.1.1**.
- `domain/model` and `domain/usecase` remain at **zero framework dependencies**. `./gradlew validateStructure` enforces the module boundaries and must stay green.
- **No JPA entity and no DTO may ever be returned from a use case or leak into `domain/`.** Adapters map at their own boundary.
- **Builder pattern** for construction; Lombok `@Builder` is allowed in `infrastructure/` and `applications/` only, never in `domain/`.
- **Constructor injection only.** No `@Autowired` on fields.
- Money stays `BigDecimal`; database columns are `NUMERIC(38,10)` to match the domain's scale 10. Never `double`, `float`, `REAL` or `DOUBLE PRECISION`.
- Time comes from the `TimeProvider` port, implemented once in `app-service` over `java.time.Clock`. No production class calls `LocalDate.now()` directly except that one implementation.
- **No secret in Git.** n8n base URL, database credentials and the JWT signing key come from environment variables with local-only defaults in `application-local.yml`.
- Run Gradle from `backend/` as `./gradlew`. The merged coverage gate (`check.dependsOn jacocoMergedCoverageVerification`, 80% LINE) stays and must stay green; merged JaCoCo tasks need `--no-configuration-cache`.
- Javadoc in **English**; Conventional Commits in **English** with the `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>` trailer.
- Test assertions use **AssertJ**. Mockito runs with **strict stubs** — never `lenient()`; an unnecessary stub means the test is over-stubbing and must be fixed, not silenced.

## Task list

| # | Task | Why it is here | Needs Docker |
|---|---|---|---|
| 1 | Fix the currency aggregation Critical | Blocking defect from Plan 1's final review | no |
| 2 | Generate the adapters, boot the app, Postgres locally | Everything else builds on a booting app | yes |
| 3 | Flyway schema and seed data | The adapters need tables; seeds migrate the real portfolio | yes |
| 4 | JPA adapter: transactions and instruments | The two repositories every read path needs | yes |
| 5 | JPA adapter: users and signal snapshots | Completes the persistence seam | yes |
| 6 | n8n consumer: quotes, exchange rate, signals | The three providers the dashboard depends on | no |
| 7 | n8n consumer: the four pass-through providers | Completes the port surface the spec names | no |
| 8 | Security: BCrypt hasher, JWT tokens, Spring Security | Protects every endpoint but login and health | no |
| 9 | REST entry point: controllers, DTOs, ProblemDetail, OpenAPI | The API the frontend will consume | no |
| 10 | app-service wiring, configuration, scheduled refresh | Turns the parts into a running application | yes |
| 11 | Cucumber acceptance suite | Proves the flows end to end against real infrastructure | yes |
| 12 | Local run: compose file, README, smoke check | The deliverable the user can actually start | yes |

---

### Task 1: Normalize portfolio aggregation to a base currency

**Files:**
- Modify: `backend/domain/model/src/main/java/co/com/david/inversiones/model/ValuedPosition.java`
- Modify: `backend/domain/model/src/main/java/co/com/david/inversiones/model/PortfolioValuation.java`
- Modify: `backend/domain/usecase/src/main/java/co/com/david/inversiones/usecase/portfolio/GetPortfolioUseCase.java`
- Modify: `backend/domain/usecase/src/main/java/co/com/david/inversiones/usecase/portfolio/EvaluateDiversificationUseCase.java`
- Modify: `backend/domain/usecase/src/main/java/co/com/david/inversiones/usecase/portfolio/CalculateTotalReturnUseCase.java`
- Test: `GetPortfolioUseCaseTest.java`, `EvaluateDiversificationUseCaseTest.java`, `CalculateTotalReturnUseCaseTest.java`

**The defect, verified.** `GetPortfolioUseCase.execute()` takes the currency of the *first* open position and reduces all three totals into it with `Money::add`, which throws `CurrencyMismatchException` when the currencies differ. A portfolio holding a COP position (TERPEL on the BVC through Trii) alongside a USD position — the exact three-segment shape spec §4.5 requires — crashes instead of valuing. `EvaluateDiversificationUseCase` has the same defect at its own aggregation: it merges `position.marketValue()` by ticker and by sector and divides by `totalMarketValue()`, so it either throws or, worse, produces a ratio mixing pesos and dollars.

No existing test in `:usecase` uses `Money.cop` at all — the test named for the Colombian exchange builds its positions with `Money.usd`. The entire peso path has never executed. That is why 12 clean per-task reviews and 116 green tests missed this.

**The decision.** Normalize at aggregation. Positions keep their native amounts; every *aggregate* is expressed in a single base currency, **USD**, converted with the TRM from `ExchangeRateProvider`. `CalculateTotalReturnUseCase` continues to read native amounts, because separating the currency effect is precisely its job.

Rejected: per-currency total maps (truthful, but the 20/25/30% ceilings are portfolio-wide by definition, so cross-currency weights would become uncomputable), and restricting `GetPortfolioUseCase` to USD positions (would strand the BVC segment that `CalculateTotalReturnUseCase` consumes).

**Interfaces:**
- Consumes: `ExchangeRateProvider.currentCopPerUsd()`, `ExchangeRate.convert(Money)`, `Money`, `Percentage`, `Currency`.
- Produces:
  - `ValuedPosition` gains two fields and accessors: `Money costInBaseCurrency`, `Money marketValueInBaseCurrency`. Native `position().totalCost()`, `marketValue()`, `unrealizedPnl()` are unchanged in meaning and stay in the position's own currency.
  - `PortfolioValuation` gains `Currency baseCurrency` and its three totals are now in that currency.
  - `GetPortfolioUseCase(TransactionRepository, QuoteProvider, ExchangeRateProvider)` — a third constructor parameter. Tests using `@InjectMocks` must declare their `@Mock` fields in that order.
  - `EvaluateDiversificationUseCase` computes every weight from `marketValueInBaseCurrency`.

- [ ] **Step 1: Write the failing tests**

Add to `GetPortfolioUseCaseTest`. The class needs a `@Mock ExchangeRateProvider exchangeRateProvider` declared **after** `quoteProvider` so `@InjectMocks` binds correctly, plus a TRM constant.

```java
    private static final java.time.LocalDate RATE_DATE = java.time.LocalDate.of(2026, 9, 29);
    private static final ExchangeRate TRM =
            ExchangeRate.copPerUsd(new java.math.BigDecimal("4000.00"), RATE_DATE);

    private static Transaction buyCop(Ticker ticker, String broker, String quantity, String price) {
        return Transaction.builder()
                .ticker(ticker).brokerName(broker).type(TransactionType.BUY)
                .quantity(Quantity.of(quantity)).unitPrice(Money.cop(price))
                .tradedAt(LocalDate.of(2026, 6, 1)).build();
    }

    @Test
    void valuesAPortfolioHoldingBothDollarAndPesoPositionsWithoutThrowing() {
        Ticker terpel = Ticker.of("TERPEL");
        when(transactionRepository.findAll()).thenReturn(List.of(
                buy(QQQ, "Hapi", "2", "100.00"),
                buyCop(terpel, "Trii", "25", "16000.00")));
        when(quoteProvider.findQuotes(Set.of(QQQ, terpel))).thenReturn(Map.of(
                QQQ, quote(QQQ, "150.00", "0.82"),
                terpel, new Quote(terpel, Money.cop("20000.00"), Percentage.of("1.5"))));
        when(exchangeRateProvider.currentCopPerUsd()).thenReturn(TRM);

        PortfolioValuation valuation = useCase.execute();

        assertThat(valuation.baseCurrency()).isEqualTo(Currency.USD);
        // QQQ: cost 200 USD, market 300 USD.
        // TERPEL: cost 400,000 COP -> 100 USD at 4000; market 500,000 COP -> 125 USD.
        assertThat(valuation.totalInvested().amount()).isEqualByComparingTo("300");
        assertThat(valuation.totalMarketValue().amount()).isEqualByComparingTo("425");
        assertThat(valuation.totalUnrealizedPnl().amount()).isEqualByComparingTo("125");
        assertThat(valuation.totalInvested().currency()).isEqualTo(Currency.USD);
    }

    @Test
    void keepsEachPositionNativeAmountsWhileReportingBaseCurrencyAggregates() {
        Ticker terpel = Ticker.of("TERPEL");
        when(transactionRepository.findAll()).thenReturn(List.of(buyCop(terpel, "Trii", "25", "16000.00")));
        when(quoteProvider.findQuotes(Set.of(terpel))).thenReturn(Map.of(
                terpel, new Quote(terpel, Money.cop("20000.00"), Percentage.of("1.5"))));
        when(exchangeRateProvider.currentCopPerUsd()).thenReturn(TRM);

        ValuedPosition valued = useCase.execute().positions().getFirst();

        assertThat(valued.marketValue().currency()).isEqualTo(Currency.COP);
        assertThat(valued.marketValue().amount()).isEqualByComparingTo("500000");
        assertThat(valued.marketValueInBaseCurrency().currency()).isEqualTo(Currency.USD);
        assertThat(valued.marketValueInBaseCurrency().amount()).isEqualByComparingTo("125");
        assertThat(valued.costInBaseCurrency().amount()).isEqualByComparingTo("100");
        // The native percentage is unaffected by the rate.
        assertThat(valued.unrealizedPnlPercent().value()).isEqualByComparingTo("25.0000");
    }

    @Test
    void doesNotConsultTheExchangeRateWhenEveryPositionIsAlreadyInTheBaseCurrency() {
        when(transactionRepository.findAll()).thenReturn(List.of(buy(QQQ, "Hapi", "2", "100.00")));
        when(quoteProvider.findQuotes(Set.of(QQQ))).thenReturn(Map.of(QQQ, quote(QQQ, "150.00", "0.82")));

        PortfolioValuation valuation = useCase.execute();

        assertThat(valuation.totalMarketValue().amount()).isEqualByComparingTo("300");
        verifyNoInteractions(exchangeRateProvider);
    }
```

Add to `EvaluateDiversificationUseCaseTest` — a mixed-currency weight case. `positionCop(...)` builds a COP position whose `marketValueInBaseCurrency` is supplied explicitly, since the use case reads only the base-currency field:

```java
    @Test
    void weighsPesoAndDollarHoldingsOnTheSameBaseCurrencyScale() {
        // TERPEL: 30 USD of base-currency market value; QQQ: 70 USD. Total 100.
        portfolioOf(positionWithBase("TERPEL", "Trii", Money.cop("120000"), Money.usd("30")),
                positionWithBase("QQQ", "Hapi", Money.usd("70"), Money.usd("70")));
        catalog("TERPEL", InstrumentType.EQUITY, "Energy");
        catalog("QQQ", InstrumentType.ETF, "Broad Market");

        DiversificationReport report = useCase.execute();

        assertThat(report.weightByTicker().get("TERPEL").value()).isEqualByComparingTo("30.0000");
        assertThat(report.violations()).extracting(DiversificationViolation::subject).contains("TERPEL");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./gradlew :usecase:test`
Expected: compilation failure — `cannot find symbol: method marketValueInBaseCurrency()`.

- [ ] **Step 3: Extend the two model types**

`ValuedPosition`: add the fields `Money costInBaseCurrency` and `Money marketValueInBaseCurrency` with their builder setters and accessors, both required via `Objects.requireNonNull`, following the existing builder shape exactly. Extend the class Javadoc: "Native amounts stay in the instrument's own currency; the base-currency amounts exist so that portfolio-wide totals and allocation weights can be computed across currencies without the caller needing a rate."

`PortfolioValuation`: add `Currency baseCurrency`, required, with its setter and accessor. Extend the Javadoc to state that the three totals are expressed in `baseCurrency`, converted at the rate in force when the valuation was produced.

- [ ] **Step 4: Rewrite the aggregation in `GetPortfolioUseCase`**

The use case takes a third constructor parameter and resolves the rate **lazily** — only when a position is not already in the base currency, which is what `doesNotConsultTheExchangeRateWhenEveryPositionIsAlreadyInTheBaseCurrency` pins:

```java
    /** Currency every portfolio-wide aggregate is expressed in. */
    private static final Currency BASE_CURRENCY = Currency.USD;

    public PortfolioValuation execute() {
        List<Position> openPositions = Position.deriveAll(transactionRepository.findAll());

        Set<Ticker> tickers = openPositions.stream()
                .map(Position::ticker)
                .collect(Collectors.toUnmodifiableSet());
        Map<Ticker, Quote> quotes = quoteProvider.findQuotes(tickers);

        boolean needsConversion = openPositions.stream()
                .anyMatch(position -> position.totalCost().currency() != BASE_CURRENCY);
        ExchangeRate rate = needsConversion ? exchangeRateProvider.currentCopPerUsd() : null;

        List<ValuedPosition> valued = openPositions.stream()
                .map(position -> value(position, quotes.get(position.ticker()), rate))
                .toList();

        Money totalInvested = valued.stream()
                .map(ValuedPosition::costInBaseCurrency)
                .reduce(Money.zero(BASE_CURRENCY), Money::add);
        Money totalMarketValue = valued.stream()
                .map(ValuedPosition::marketValueInBaseCurrency)
                .reduce(Money.zero(BASE_CURRENCY), Money::add);
        Money totalUnrealizedPnl = totalMarketValue.subtract(totalInvested);
        ...
                .baseCurrency(BASE_CURRENCY)
    }

    /**
     * Converts an amount into {@link #BASE_CURRENCY}.
     *
     * <p>The rate converts USD into COP, so a peso amount is divided by it rather than multiplied.
     */
    private Money toBaseCurrency(Money amount, ExchangeRate rate) {
        if (amount.currency() == BASE_CURRENCY) {
            return amount;
        }
        return Money.of(amount.amount().divide(rate.rate(), Money.SCALE, RoundingMode.HALF_UP),
                BASE_CURRENCY);
    }
```

`value(...)` gains the `rate` parameter and sets both new fields: for a priced position, `costInBaseCurrency = toBaseCurrency(position.totalCost(), rate)` and `marketValueInBaseCurrency = toBaseCurrency(marketValue, rate)`; for an unpriced one, both are `toBaseCurrency(position.totalCost(), rate)`, preserving the carried-at-cost rule.

Note `totalUnrealizedPnl` is now derived as market value minus invested rather than summed from the per-position native P&L — summing native P&L would reintroduce the currency mix, and the two are equal by construction.

- [ ] **Step 5: Point `EvaluateDiversificationUseCase` at the base-currency amounts**

Replace both `position.marketValue()` reads with `position.marketValueInBaseCurrency()`. Add to the class Javadoc: "Weights are computed on base-currency market value, so a peso holding and a dollar holding sit on the same scale; a ratio mixing the two would be meaningless."

- [ ] **Step 6: Make `CalculateTotalReturnUseCase` convert rather than relabel**

In `summarize(...)`, replace `Money.of(position.position().totalCost().amount(), currency)` and the market-value equivalent — which silently *renamed* a currency — with a real check: if the amount's currency already equals the segment currency, use it as is; otherwise convert with the rate the use case already holds. An amount whose currency disagrees with its segment after conversion is a programming error, so let `Money.add` raise rather than papering over it.

Keep every existing expected value in `CalculateTotalReturnUseCaseTest` unchanged — the segments' arithmetic does not move. The test helpers build positions with `Money.usd`, so they will need the two new `ValuedPosition` fields supplied; set both to the same USD amounts they already use.

- [ ] **Step 7: Run everything**

```bash
cd backend && ./gradlew :model:test :usecase:test
./gradlew clean build validateStructure --no-configuration-cache
```
Expected: all green, coverage gate passing. `:usecase` should gain 4 tests.

- [ ] **Step 8: Commit**

```bash
git add backend/domain
git commit -m "$(cat <<'MSG'
fix(usecase): aggregate portfolio totals in a single base currency

Summing position totals took the first position's currency and added the
rest into it, so a portfolio holding both a peso position on the BVC and
a dollar position threw instead of valuing, and allocation weights mixed
the two into a meaningless ratio. No test used pesos, so the whole path
had never run.

Positions keep their native amounts and every portfolio-wide aggregate is
now expressed in dollars, converted with the TRM, which is what the
diversification ceilings need to be comparable at all. The rate is only
consulted when a position is not already in the base currency.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

### Task 2: Generate the adapters and boot the application against a local PostgreSQL

**Goal of this task.** End it with a running application: `docker compose up` gives a PostgreSQL, `./gradlew :app-service:bootRun` starts Spring Boot, and `/actuator/health` returns `UP`. No business behaviour is added. Every later task in this plan depends on this working, so it is deliberately small and verifiable.

**Files:**
- Generated by the scaffold: `backend/infrastructure/driven-adapters/jpa-repository/`, `backend/infrastructure/driven-adapters/rest-consumer/`, `backend/infrastructure/entry-points/api-rest/`
- Create: `deploy/docker-compose.dev.yml`, `deploy/.env.template`
- Create: `backend/applications/app-service/src/main/resources/application.yml`, `application-local.yml`
- Modify: `backend/applications/app-service/build.gradle` (Flyway, Actuator — only if the scaffold did not add them)

**Interfaces:**
- Consumes: nothing from the domain yet.
- Produces: three new Gradle modules on the build, a booting Spring context, and a local database the next tasks migrate. **Report the exact module paths and generated class names in your report** — later tasks need them and I will not guess.

- [ ] **Step 1: Generate the three adapter skeletons**

Run from `backend/`, one at a time, checking each succeeds before the next:
```bash
./gradlew gda --type=JPA
./gradlew gda --type=RESTCONSUMER
./gradlew gep --type=RESTMVC
```
Then record what appeared:
```bash
git status --short
./gradlew projects --no-configuration-cache | grep -i "project '"
```
Expected: `BUILD SUCCESSFUL` for each, and three new subprojects. The scaffold may also add dependencies to `app-service/build.gradle` and create sample classes — leave its output alone for now; Task 4 replaces the samples.

- [ ] **Step 2: Verify the build still passes and the domain is still clean**

```bash
./gradlew build validateStructure --no-configuration-cache
cd .. && grep -rE 'import (org\.springframework|jakarta|lombok)' --include='*.java' backend/domain/ && echo "FRAMEWORK LEAK" || echo "domain is clean"
```
Both must pass. `validateStructure` failing here means the generated modules broke the layering and nothing else should proceed.

If the coverage gate fails because the generated sample classes are untested, do **not** lower the threshold. Add the generated samples to the gate's exclusion list only if they are scaffold placeholders that Task 4 will delete; note in your report exactly what you excluded and why.

- [ ] **Step 3: Write the local PostgreSQL compose file**

Create `deploy/docker-compose.dev.yml`. This is the development database only — the backend container comes in Task 12.

```yaml
name: inversiones-dev

services:
  postgres:
    image: postgres:17-alpine
    container_name: inversiones-postgres
    environment:
      POSTGRES_DB: ${POSTGRES_DB:-inversiones}
      POSTGRES_USER: ${POSTGRES_USER:-inversiones}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-inversiones_local}
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-inversiones} -d ${POSTGRES_DB:-inversiones}"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  postgres-data:
```

Create `deploy/.env.template` documenting every variable with a safe local default and a comment saying the real values live in an untracked `.env`:

```dotenv
# Local development defaults. Copy to .env and change nothing for local work.
POSTGRES_DB=inversiones
POSTGRES_USER=inversiones
POSTGRES_PASSWORD=inversiones_local
POSTGRES_PORT=5432

# Backend. JWT_SECRET must be at least 32 bytes; this default is for local use only.
JWT_SECRET=local-development-secret-change-me-32bytes
JWT_EXPIRATION_MINUTES=480

# n8n. The real base URL is not a secret but is environment specific.
N8N_BASE_URL=https://n8n.srv1449561.hstgr.cloud/webhook/inv
```

Confirm `.gitignore` already ignores `deploy/.env` and does not ignore `deploy/.env.template`. It should — verify rather than assume.

- [ ] **Step 4: Configure the application**

`application.yml` holds what is true everywhere; `application-local.yml` holds the local wiring. Every value reads from an environment variable with a local default, so nothing secret is committed and nothing needs editing to run locally.

```yaml
spring:
  application:
    name: inversiones
  profiles:
    default: local
  threads:
    virtual:
      enabled: true
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true

inversiones:
  base-trm: ${BASE_TRM:3568.42}
  n8n:
    base-url: ${N8N_BASE_URL:https://n8n.srv1449561.hstgr.cloud/webhook/inv}
    connect-timeout: 5s
    read-timeout: 30s
  security:
    jwt-secret: ${JWT_SECRET:local-development-secret-change-me-32bytes}
    jwt-expiration-minutes: ${JWT_EXPIRATION_MINUTES:480}
```

`ddl-auto: validate` is deliberate: Hibernate must never create or alter a table. Flyway owns the schema, and `validate` turns a drift between the entities and the migrations into a startup failure instead of a silent mutation.

`application-local.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:inversiones}
    username: ${POSTGRES_USER:inversiones}
    password: ${POSTGRES_PASSWORD:inversiones_local}

logging:
  level:
    co.com.david.inversiones: DEBUG
```

Add `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` and `spring-boot-starter-actuator` to the module that needs them — check first whether the scaffold's JPA adapter already brought some in, and do not duplicate a dependency. Versions come from the Spring Boot platform; do not pin any.

- [ ] **Step 5: Create a placeholder migration so Flyway has something to run**

`backend/applications/app-service/src/main/resources/db/migration/V1__baseline.sql` — Task 3 replaces this with the real schema. For now:
```sql
-- Baseline migration. The real schema arrives in task 3 of this plan.
CREATE TABLE IF NOT EXISTS flyway_baseline (
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- [ ] **Step 6: Prove it boots**

```bash
docker compose -f deploy/docker-compose.dev.yml up -d
docker compose -f deploy/docker-compose.dev.yml ps          # postgres healthy
cd backend && ./gradlew :app-service:bootRun --no-configuration-cache &
# wait for startup, then:
curl -s localhost:8080/actuator/health
```
Expected: `{"status":"UP"}` with the database component up. Then stop the application.

Capture the real output of the health call in your report. If the context fails to start, that is the finding — report it with the stack trace rather than working around it.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "$(cat <<'MSG'
build(infrastructure): generate the adapters and boot against local postgres

Generate the JPA and REST consumer driven adapters and the REST MVC entry
point with the scaffold, then wire a local PostgreSQL through docker
compose so the application starts and reports healthy.

Hibernate runs with ddl-auto validate: Flyway owns the schema, so a drift
between the entities and the migrations fails startup instead of silently
altering a table. Every setting reads an environment variable with a
local default, so nothing secret is committed and local work needs no
edits.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

### Task 3: Flyway schema and seed data

**Goal.** The real schema, plus the seeds that migrate the portfolio out of `index.html` and into the database. After this task the current holdings exist as rows, which is the point of the whole migration: changing the portfolio stops meaning editing JavaScript.

**Files:**
- Replace: `backend/applications/app-service/src/main/resources/db/migration/V1__baseline.sql` → `V1__initial_schema.sql`
- Create: `V2__seed_reference_data.sql`, `V3__seed_current_portfolio.sql`
- Test: `backend/applications/app-service/src/test/java/co/com/david/inversiones/MigrationTest.java`

**Interfaces:**
- Consumes: nothing from the domain — SQL only.
- Produces: the eight tables Task 4 and Task 5 map entities onto. Their column names are the contract those tasks code against.

**Design rules this schema follows, and why:**

- **Money is `NUMERIC(38,10)`.** The domain normalizes every amount to scale 10, so the column must hold scale 10 exactly. `DOUBLE PRECISION` or `REAL` would silently round and is forbidden.
- **Every monetary column is paired with its currency.** A bare amount with no currency is how the Plan 1 Critical happened; the schema does not allow it.
- **Timestamps are `TIMESTAMPTZ`**, never `TIMESTAMP`. Trade dates are `DATE`, because a trade happens on a day, not at an instant.
- **`signal_snapshots` keeps both the parsed scores and the raw payload** in `JSONB`. The scores are what the domain reads; the raw payload is what lets a future change re-derive a score from data already captured, without re-calling n8n for a day that has passed.
- Identifiers are `BIGGENERATED` (`GENERATED BY DEFAULT AS IDENTITY`), not `SERIAL`, which is the modern form and keeps the sequence owned by the column.

- [ ] **Step 1: Write the failing test**

`MigrationTest.java` — a Testcontainers test that runs the migrations against a real PostgreSQL and asserts the schema is what the later tasks expect. This is the test that catches a migration that parses but produces the wrong shape.

```java
package co.com.david.inversiones;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class MigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsEveryTableTheAdaptersMapOnto() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains("users", "brokers", "instruments", "transactions",
                "exchange_rates", "signal_snapshots", "ai_syntheses");
    }

    @Test
    void storesMonetaryAmountsAtScaleTenSoTheDomainScaleSurvivesARoundTrip() {
        List<String> types = jdbc.queryForList(
                """
                SELECT data_type || '(' || numeric_precision || ',' || numeric_scale || ')'
                FROM information_schema.columns
                WHERE table_name = 'transactions' AND column_name IN ('unit_price', 'fees')
                """,
                String.class);

        assertThat(types).isNotEmpty().allMatch(type -> type.equals("numeric(38,10)"));
    }

    @Test
    void seedsTheCurrentPortfolioSoTheDashboardHasSomethingToShow() {
        Integer transactions = jdbc.queryForObject("SELECT count(*) FROM transactions", Integer.class);
        Integer instruments = jdbc.queryForObject("SELECT count(*) FROM instruments", Integer.class);

        assertThat(instruments).isGreaterThanOrEqualTo(18);
        assertThat(transactions).isGreaterThanOrEqualTo(18);
    }

    @Test
    void seedsExactlyOneUserWithAHashedPassword() {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM users", String.class);

        assertThat(hashes).hasSize(1);
        assertThat(hashes.getFirst()).startsWith("$2");
    }

    @Test
    void rejectsTwoInstrumentsWithTheSameTicker() {
        List<String> indexes = jdbc.queryForList(
                """
                SELECT indexdef FROM pg_indexes
                WHERE tablename = 'instruments' AND indexdef LIKE '%UNIQUE%'
                """,
                String.class);

        assertThat(indexes).anyMatch(definition -> definition.contains("ticker"));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend && ./gradlew :app-service:test --tests '*MigrationTest*' --no-configuration-cache`
Expected: failure — the tables do not exist yet. Docker must be running for Testcontainers.

- [ ] **Step 3: Write `V1__initial_schema.sql`**

```sql
-- Core schema for the investments platform.
-- Every monetary amount is NUMERIC(38,10) to match the domain's scale 10, and is paired with
-- the currency it is denominated in: an amount without its currency is not a usable figure.

CREATE TABLE users (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_username_unique UNIQUE (username)
);

CREATE TABLE brokers (
    id   BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    CONSTRAINT brokers_name_unique UNIQUE (name)
);

CREATE TABLE instruments (
    id       BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    ticker   VARCHAR(10) NOT NULL,
    name     VARCHAR(120) NOT NULL,
    type     VARCHAR(10)  NOT NULL,
    sector   VARCHAR(60)  NOT NULL,
    exchange VARCHAR(20)  NOT NULL,
    currency CHAR(3)      NOT NULL,
    CONSTRAINT instruments_ticker_unique UNIQUE (ticker),
    CONSTRAINT instruments_type_valid    CHECK (type IN ('EQUITY', 'ETF', 'ADR')),
    CONSTRAINT instruments_currency_valid CHECK (currency IN ('USD', 'COP'))
);

CREATE TABLE transactions (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    instrument_id BIGINT         NOT NULL REFERENCES instruments (id),
    broker_id     BIGINT         NOT NULL REFERENCES brokers (id),
    type          VARCHAR(4)     NOT NULL,
    quantity      NUMERIC(38,10) NOT NULL,
    unit_price    NUMERIC(38,10) NOT NULL,
    fees          NUMERIC(38,10) NOT NULL DEFAULT 0,
    currency      CHAR(3)        NOT NULL,
    traded_at     DATE           NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT transactions_type_valid     CHECK (type IN ('BUY', 'SELL')),
    CONSTRAINT transactions_currency_valid CHECK (currency IN ('USD', 'COP')),
    CONSTRAINT transactions_quantity_positive CHECK (quantity > 0),
    CONSTRAINT transactions_fees_not_negative CHECK (fees >= 0)
);

-- Positions are always derived by replaying these rows, so the read path is
-- (instrument, broker, date) ordered.
CREATE INDEX transactions_instrument_broker_traded_idx
    ON transactions (instrument_id, broker_id, traded_at);

CREATE TABLE exchange_rates (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    base_currency  CHAR(3)        NOT NULL,
    quote_currency CHAR(3)        NOT NULL,
    rate           NUMERIC(38,10) NOT NULL,
    rate_date      DATE           NOT NULL,
    CONSTRAINT exchange_rates_unique_per_day UNIQUE (base_currency, quote_currency, rate_date),
    CONSTRAINT exchange_rates_rate_positive  CHECK (rate > 0)
);

CREATE TABLE signal_snapshots (
    id             BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    instrument_id  BIGINT        NOT NULL REFERENCES instruments (id),
    snapshot_date  DATE          NOT NULL,
    technical_score NUMERIC(5,2),
    news_score      NUMERIC(5,2),
    analyst_score   NUMERIC(5,2),
    insider_score   NUMERIC(5,2),
    social_score    NUMERIC(5,2),
    wsb_score       NUMERIC(5,2),
    combined_score  NUMERIC(5,2),
    signal_level    VARCHAR(12),
    -- The raw provider payload, so a future scoring change can be recomputed over days already
    -- captured instead of being lost to history.
    raw_payload     JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT signal_snapshots_unique_per_day UNIQUE (instrument_id, snapshot_date),
    CONSTRAINT signal_snapshots_level_valid CHECK (
        signal_level IS NULL
        OR signal_level IN ('STRONG_BUY', 'BUY', 'HOLD', 'SELL', 'STRONG_SELL'))
);

CREATE INDEX signal_snapshots_instrument_date_idx
    ON signal_snapshots (instrument_id, snapshot_date DESC);

CREATE TABLE ai_syntheses (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    instrument_id BIGINT       NOT NULL REFERENCES instruments (id),
    snapshot_date DATE         NOT NULL,
    ia_score      NUMERIC(5,2),
    signal        VARCHAR(20),
    conviction    VARCHAR(20),
    horizon       VARCHAR(40),
    summary       TEXT,
    catalysts     TEXT,
    risks         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ai_syntheses_unique_per_day UNIQUE (instrument_id, snapshot_date)
);
```

Per-source score columns are `NUMERIC(5,2)` because they are 1-to-10 scores, not money; they are nullable because a missing source is the normal case the domain redistributes weight around. `combined_score` and `signal_level` are nullable for the same reason.

- [ ] **Step 4: Write `V2__seed_reference_data.sql`**

Seed the three brokers, the eighteen instruments the workflows cover, and the single user. Sector and exchange values must match what `EvaluateDiversificationUseCase` and `CalculateTotalReturnUseCase` read: the segment rule keys on broker `Trii` plus exchange `BVC`, so TERPEL and MINEROS must have exchange `BVC` and the rest must not.

Instruments, taken from `AGENT_SPEC.md`: QQQ, VOO, SMH, GLD, IBIT as `ETF`; TSLA, GOOGL, TTWO, NU, TSM, AMZN, MSFT, NLR, SPCX, PLTR, UNH, JPM as `EQUITY`; EC as `ADR`; TERPEL and MINEROS as `EQUITY` on `BVC` in `COP`. Give each a real sector — the 30% sector ceiling is meaningless if everything lands in one bucket.

The user row: username `david`, and a **BCrypt hash of a known local password**. Generate it rather than inventing a string — a hash that does not verify would make the login flow untestable. Put the plaintext nowhere but the migration's own comment, and note in your report what it is so the acceptance tests can use it.

- [ ] **Step 5: Write `V3__seed_current_portfolio.sql`**

Translate `PORT_USD`, `TRII_USD` and `PORT_BVC` from `AGENT_SPEC.md` into `transactions` rows. Each holding becomes one `BUY`. The accumulated quantity and cost are known, so derive the unit price as cost divided by quantity at scale 10 — the domain recomputes the average cost from these rows and must arrive at the same figures.

Use `traded_at = '2026-06-30'`, the date `AGENT_SPEC.md` records for the last diversified purchase, and add a comment saying these are consolidated opening positions rather than individual historical trades, because that is the honest description of what the data is.

Also seed one `exchange_rates` row: base `USD`, quote `COP`, rate `3568.42`, dated `2026-06-30` — the reference TRM the total-return calculation compares against.

- [ ] **Step 6: Run the test to verify it passes**

```bash
cd backend && ./gradlew :app-service:test --tests '*MigrationTest*' --no-configuration-cache
```
Expected: PASS, 5 tests. Then confirm the migrations also apply to the running local database:
```bash
cd backend && ./gradlew :app-service:bootRun --no-configuration-cache &
curl -s localhost:8080/actuator/health
docker exec inversiones-postgres psql -U inversiones -d inversiones \
  -c "SELECT count(*) FROM transactions;" -c "SELECT ticker, exchange FROM instruments WHERE exchange = 'BVC';"
```
Report the real row counts.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "$(cat <<'MSG'
feat(jpa): add the schema and migrate the portfolio out of the dashboard

Create the eight tables the adapters map onto, with every monetary column
at NUMERIC(38,10) to match the domain's scale and paired with its
currency, since an amount without its currency is how the aggregation
defect happened.

Seed the brokers, the eighteen instruments and the current holdings, so
changing the portfolio stops meaning editing JavaScript constants. Keep
the raw provider payload alongside the parsed scores so a future scoring
change can be recomputed over days already captured.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
)"
```

---

## Tasks 4 to 12 — fidelity note

Tasks 1 to 3 carry full source because they encode decisions that are easy to get wrong silently.
Tasks 4 onward are specified as **contracts plus the tests that pin them**: the exact behaviour
required, the exact test cases, and the constraints — but not every line of adapter boilerplate. Two
reasons. First, the scaffold generates the adapter skeletons and their class names, so writing their
bodies here would be guessing at names Task 2's report establishes. Second, where Plan 1's full-source
approach paid for itself was in arithmetic and business rules, which are now done; what remains is
mapping and wiring, where a precise contract plus a failing test is enough to make the work
unambiguous.

Where a task below says "following the contract", the implementer writes the code and the review
checks it against the named behaviour and tests.

---

### Task 4: JPA adapter for transactions and instruments

**Depends on Task 2's report** for the generated module path and sample class names to replace.

**Produces:** `TransactionRepository` and `InstrumentRepository` implementations, JPA entities for
`transactions` and `instruments`, and mappers between entity and domain.

**Contract:**
- Entities `TransactionData` and `InstrumentData` live in the adapter, annotated with `jakarta.persistence`, using Lombok `@Builder` (allowed in infrastructure). They are **never** returned from the adapter — every public method returns domain types.
- Mapping is explicit hand-written code, not a reflection-based mapper. A mapper that silently drops a field is the failure this avoids, and `Money`/`Quantity`/`Ticker` need deliberate conversion anyway.
- `TransactionData` stores `currency` as a column and reconstitutes `Money.of(amount, Currency.valueOf(currency))`. The currency travels with the amount in both directions.
- `findByTickerAndBroker(Ticker, String)` is a derived query joining both foreign keys; it must not load all transactions and filter in Java.
- Saving a `Transaction` whose instrument or broker is absent from the catalog raises `UnknownInstrumentException` rather than a constraint violation surfacing as a 500.

**Tests** — `@DataJpaTest` with Testcontainers PostgreSQL, Flyway applied:
1. A saved transaction round-trips: every field equal, `Money` scale preserved at 10, currency preserved.
2. A COP transaction round-trips as COP, not silently as USD. (This is the Plan 1 Critical's shape; the persistence layer must not reintroduce it.)
3. `findByTickerAndBroker` returns only the matching pair, with a fixture containing the same ticker at two brokers and a different ticker at the same broker.
4. `findById` on an unknown id returns `Optional.empty()`, not null.
5. `deleteById` removes exactly one row.
6. `findByTicker` on `InstrumentRepository` is case-insensitive in the sense that `Ticker.of` already normalizes — assert `findByTicker(Ticker.of("qqq"))` finds the seeded QQQ.
7. The seeded portfolio is readable: `findAll()` returns at least 18 transactions after migrations.

**Also in this task:** replace the scaffold's `UseCasesConfigTest` tautology. It currently catches
`UnsatisfiedDependencyException` and asserts `assertTrue(true, ...)`, so it can never fail. Leave it
until the adapters exist, then — in Task 10 — it becomes a real context test. Note here that the
dependency is now half-satisfiable, and record in the report which gateways still lack a bean.

---

### Task 5: JPA adapter for users and signal snapshots

**Produces:** `UserRepository` and `SignalSnapshotRepository` implementations plus their entities.

**Contract:**
- `SignalSnapshotData` maps the six per-source score columns to a `Map<SignalSource, BigDecimal>`, **omitting nulls** so the domain's "absent source" semantics survive the round trip. A null column must not become a zero score — that is the failure mode the domain's weight redistribution exists to avoid.
- `raw_payload` is `JSONB`, written as a `String` through a `@JdbcTypeCode(SqlTypes.JSON)` column. The adapter does not parse it; it is captured for future recomputation.
- `saveAll(List)` is a single batch, not a loop of `save` calls.
- `findLatest(Ticker)` orders by `snapshot_date DESC` and limits to one, in SQL.
- `findBetween(Ticker, from, to)` is inclusive at both ends and ordered ascending by date.

**Tests** — `@DataJpaTest` with Testcontainers:
1. A snapshot with three of six sources present round-trips with exactly those three in `availableSources()`, and `scoreOf` is empty for the other three.
2. A snapshot whose `insider_score` column is NULL comes back with `INSIDER` **absent**, not present-as-zero.
3. `findLatest` returns the most recent by date when three snapshots exist for one ticker.
4. `findBetween` includes both boundary dates and excludes a snapshot one day outside each end.
5. `saveAll` of three snapshots issues one batch and returns three.
6. `findByUsername` returns the seeded user; an unknown username returns `Optional.empty()`.
7. The stored `password_hash` is not readable through `User.toString()`.

---

### Task 6: n8n consumer for quotes, exchange rate and signals

**Produces:** `QuoteProvider`, `ExchangeRateProvider` and `SignalProvider` implementations in the
`rest-consumer` adapter, with Resilience4j and a Caffeine cache.

**Contract:**
- One `RestClient` bean configured from `inversiones.n8n.base-url`, connect timeout 5s, read timeout 30s.
- Endpoints: `/precios-hoy`, `/trm-hoy`, `/bvc-precios`, `/indicadores-tecnicos`, `/news-sentiment`, `/ratings-analistas`, `/insider-trading`, `/stocktwits`, `/reddit`.
- `SignalProvider.fetchSnapshots` calls the six signal endpoints and assembles one `SignalSnapshot` per ticker. **A failing endpoint contributes no score for its source rather than failing the batch** — that is what the domain's weight redistribution is for, and it is the whole degradation story of spec §4.8.
- `QuoteProvider.findQuotes` merges `/precios-hoy` (USD) and `/bvc-precios` (COP), and **must set each quote's currency from its source**: a BVC price is `Money.cop`. Getting this wrong reintroduces the Plan 1 Critical from the other side.
- Deserialization tolerates unknown fields (`FAIL_ON_UNKNOWN_PROPERTIES` off) so an upstream addition does not break the adapter.
- Resilience4j circuit breaker and retry per endpoint; Caffeine cache with a per-endpoint TTL. The TRM is cached for the day; quotes for 5 minutes.
- No API key and no secret appears in code or logs. The base URL comes from configuration.

**Tests** — MockWebServer with fixture payloads captured from the real endpoints, stored under `src/test/resources/n8n/`:
1. A successful `/trm-hoy` yields an `ExchangeRate` with the right rate and date.
2. A BVC price yields a `Quote` in **COP**; a NASDAQ price yields one in **USD**.
3. A 500 from one signal endpoint leaves that source absent from the snapshot and the other five present.
4. A read timeout is handled the same way as a 500.
5. Malformed JSON does not propagate a Jackson exception to the caller.
6. An unknown field in the payload does not fail deserialization.
7. Repeated calls within the TTL hit the cache — one upstream request for two calls.
8. The circuit breaker opens after the configured failure count and stops calling upstream.

---

### Task 7: n8n consumer for the four pass-through providers

**Produces:** `AiSynthesisProvider`, `EarningsProvider`, `WatchlistProvider`, `FundamentalsProvider`
ports (declared in `domain/model/gateways`) plus their `rest-consumer` implementations, and the four
pass-through use cases spec §4.5 names: `GetAiSynthesisUseCase`, `GetEarningsUseCase`,
`GetWatchlistUseCase`, `GetFundamentalsUseCase`.

**Contract:**
- Domain types `AiSynthesis`, `EarningsEvent`, `WatchlistItem`, `Fundamentals` — hand-written builders, no framework.
- `GetAiSynthesisUseCase` **excludes tickers the provider marked `sin_datos`** from any average it reports, and surfaces them as a separate list. Same rule as the signals dashboard, same reason.
- `GetEarningsUseCase` orders by proximity and computes days-remaining through `TimeProvider`, never `LocalDate.now()`.
- `/sintesis-ia` takes about 55 seconds upstream but is served from n8n's own cache; the read timeout must accommodate a cold call, and the adapter falls back to the last persisted snapshot rather than failing the request.

**Tests:** MockWebServer for each provider — a happy path, an upstream failure, and for AI synthesis specifically a payload containing a `sin_datos` ticker asserting it is excluded from the average and listed separately. Mockito tests for the four use cases against mocked ports.

---

### Task 8: Security — BCrypt, JWT, Spring Security

**Produces:** `PasswordHasher` and `TokenProvider` implementations in a `security-jwt` adapter, plus
the Spring Security filter chain.

**Contract:**
- `PasswordHasher` wraps `BCryptPasswordEncoder`. `matches` delegates to the encoder, which is constant-time; the domain never learns the algorithm.
- `TokenProvider` issues an HS256 JWT with `sub` = username, `iat`, `exp` from `inversiones.security.jwt-expiration-minutes`, signed with `inversiones.security.jwt-secret`. It reads the clock through `TimeProvider`, so token expiry is testable without sleeping.
- The secret must be at least 32 bytes; a shorter one fails fast at startup with a clear message rather than at first login.
- Stateless: `SessionCreationPolicy.STATELESS`, CSRF disabled (there is no session cookie), a `OncePerRequestFilter` validating the bearer token.
- Public: `POST /api/v1/auth/login`, `/actuator/health`, `/actuator/info`, and the OpenAPI/Swagger paths. **Everything else requires a valid token.**
- CORS allows exactly the origins in `inversiones.security.allowed-origins`, defaulting to `http://localhost:5173` for the Vite dev server. No wildcard.
- A rejected request returns `401` as a `ProblemDetail`, never a stack trace and never a hint about which half of the credentials failed.

**Tests:**
1. A hash produced by `hash` verifies with `matches`; a different password does not.
2. An issued token validates and carries the right subject.
3. A token past its expiry is rejected — driven by advancing the injected `TimeProvider`, with no `Thread.sleep`.
4. A token signed with a different secret is rejected.
5. A tampered payload is rejected.
6. A secret shorter than 32 bytes fails context startup.
7. `@SpringBootTest` + MockMvc: a protected endpoint without a token returns 401; with a valid token returns 200.
8. A preflight `OPTIONS` from the allowed origin succeeds; from another origin it does not.

---

### Task 9: REST entry point

**Produces:** the controllers, request/response DTOs, exception handling and OpenAPI document for the
fifteen endpoints in spec §4.6.

**Contract:**
- One controller per resource: auth, portfolio, transactions, signals, ai-synthesis, earnings, watchlist, fundamentals, diversification.
- DTOs are records in the entry point, with Jakarta Validation annotations. **No domain type and no JPA entity is serialized** — mapping is explicit.
- Errors via `@RestControllerAdvice` producing RFC 7807 `ProblemDetail`: `DomainException` subclasses map to 400 except `TransactionNotFoundException` → 404 and `InvalidCredentialsException` → 401; anything unmapped → 500 with a generated correlation id in the body and the stack trace only in the log.
- `springdoc-openapi` publishes `/v3/api-docs` and `/swagger-ui`. Every endpoint documents its response shape.
- Money is serialized as a string, not a JSON number, so no client can lose scale to a double. Each amount is an object `{"amount": "300.0000000000", "currency": "USD"}`.

**Tests:** `@SpringBootTest` + MockMvc per endpoint — the success shape, the validation failure shape,
401 without a token, and for `/portfolio` a case containing both a USD and a COP position asserting
the base currency is reported. Money-as-string is asserted explicitly, because a regression to a
numeric type would be invisible until a client rounded a figure.

---

### Task 10: Wiring, configuration and the scheduled refresh

**Produces:** `app-service` beans for every use case, the `TimeProvider` implementation, typed
configuration properties, and the scheduled snapshot refresh.

**Contract:**
- `UseCasesConfig` declares one bean per use case with constructor injection. `base-trm` is injected into `CalculateTotalReturnUseCase` from `inversiones.base-trm`.
- `TimeProvider` is implemented once over `java.time.Clock` with a `Clock.systemUTC()` bean, so tests can substitute a fixed clock.
- `@ConfigurationProperties` records for the `inversiones.*` tree, validated at startup.
- `RefreshSignalSnapshotUseCase` runs on a `@Scheduled` cron at 10:15 Bogotá time on weekdays — after the upstream workflows have refreshed, per `AGENT_SPEC.md`. Scheduling lives in `app-service`, never in the domain.
- **Replace the scaffold's `UseCasesConfigTest`.** It catches `UnsatisfiedDependencyException` and asserts `assertTrue(true, ...)`, so it cannot fail. Now that every gateway has an implementation, the context must start, and a test that cannot fail would hide a broken application. Replace it with a `@SpringBootTest` that starts the full context against Testcontainers and asserts every use case bean is present by name.

**Tests:** the real context test above; a test that a fixed `Clock` makes `TimeProvider.today()`
deterministic; and a test that the scheduled method is registered with the expected cron, without
waiting for it to fire.

---

### Task 11: Cucumber acceptance suite

**Produces:** a separate `acceptanceTest` Gradle task running Gherkin scenarios against the fully
wired application, with Testcontainers PostgreSQL and WireMock standing in for n8n.

**Contract:**
- Features in English under `src/acceptanceTest/resources/features/`. Step definitions drive the real HTTP API, not the use cases directly — an acceptance test that bypasses the controller is not testing the product.
- WireMock is stubbed from the same fixture payloads Task 6 captured, so the two layers agree about what n8n returns.
- The suite runs via `./gradlew acceptanceTest` and does not run as part of `test`, so the unit suite stays fast.
- Scenarios cover the six groups spec §6.2 names: authentication; the full transaction lifecycle; profit and loss with the exchange effect reported separately; combined scoring with sources unavailable; the diversification ceilings; and graceful degradation when n8n is down.

**The scenario that matters most**, because it is the one a unit test cannot prove:
```gherkin
  Scenario: A mixed currency portfolio is valued without the currency effect being hidden
    Given the seeded portfolio holds QQQ at Hapi in dollars and TERPEL at Trii in pesos
    And the current exchange rate is 3912.40 COP per USD
    When the user requests the total return report
    Then the asset gain and the exchange rate effect are reported as separate amounts
    And the portfolio total is expressed in a single declared base currency
```

---

### Task 12: Make it run locally

**Produces:** the one command the user actually needs, and the documentation for it.

**Contract:**
- `backend/deployment/Dockerfile`: multi-stage, `eclipse-temurin:25-jdk` to build and `:25-jre` to run, non-root user, `HEALTHCHECK` against `/actuator/health`, `-XX:MaxRAMPercentage=75`.
- `deploy/docker-compose.dev.yml` gains the backend service alongside PostgreSQL, wired by service name, with the backend waiting on the database's healthcheck.
- `README.md` gets a "Run it locally" section with the exact commands and what each proves.
- A `scripts/smoke.sh` that starts the stack, waits for health, logs in, calls `/api/v1/portfolio`, asserts the response contains a base currency, and exits non-zero on any failure. This is the script Plan 4's pipeline will reuse against a deployed environment.

**Verification — the deliverable of this whole plan:**
```bash
docker compose -f deploy/docker-compose.dev.yml up -d --build
bash scripts/smoke.sh          # exits 0
cd backend && ./gradlew test acceptanceTest --no-configuration-cache
```

## Definition of Done (Plan 2)

- [ ] `./gradlew clean build validateStructure --no-configuration-cache` green, merged coverage ≥80%.
- [ ] `./gradlew acceptanceTest` green: all six scenario groups.
- [ ] `docker compose -f deploy/docker-compose.dev.yml up -d --build` then `bash scripts/smoke.sh` exits 0.
- [ ] `domain/` still has zero framework imports; `validateStructure` still green.
- [ ] No JPA entity or DTO is reachable from `domain/`; no domain type is serialized by the API.
- [ ] No secret in Git; `deploy/.env.template` documents every variable.
- [ ] The scaffold's tautological `UseCasesConfigTest` is replaced by a context test that can fail.
- [ ] Nothing in this plan required a cloud account, VPS access or a GitHub webhook.

## Out of scope (Plan 3 and Plan 4)

Plan 3 is the React frontend. Plan 4 is deployment: the Vercel project, the Hostinger VPS compose
file behind the existing Traefik, and Jenkins with a GitHub webhook. None of it is touched here.
