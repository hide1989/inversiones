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
