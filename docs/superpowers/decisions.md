# Decisiones tomadas durante la ejecución de los Planes 1 y 2

Este documento recoge las decisiones que el agente controlador tomó sin consultar al autor del
proyecto durante la ejecución, con su motivo y el coste de equivocarse. Vive en el repositorio,
versionado, porque los ledgers de ejecución están fuera de Git y estas decisiones deben sobrevivirlos.

Cada una es revertible. Si alguna no te convence, el motivo registrado es el punto de partida para
discutirla.

## Plan 1 — Núcleo de dominio

1. # SDD ledger — plan: docs/superpowers/plans/2026-09-29-backend-domain-core.md Spec: spec.md (read; binding authority) Branch: feat/backend-domain-core Workspace isolation: dedicated branch on a repo with no other work in flight. Ruling: used a branch instead of a git worktree — the repository contains only this project's planning docs, so a worktree adds indirection without isolating anything. Cost if wrong: none material; work can be moved to a worktree at any time.

2. ## Pre-flight conflict scan ### Cross-task shared surfaces (producer -> consumer) | Producer | Consumer | Produced vs consumed | Finding | |---|---|---|---| | T1 module layout | T2..T12 | `domain/model`, `domain/usecase` source roots | OK | | T1 `main.gradle` deps block | T12 `main.gradle` subprojects block | T1 appends into `dependencies{}`, T12 appends `jacocoTestCoverageVerification` + `check.dependsOn` at `subprojects{}` level | Same file, different nesting. No conflict; T12 must append, not rewrite | | T2 `Money`, `Currency` | T3,T4,T5,T7,T8,T11 | `Money.usd/cop/of/add/subtract/multiply/divide/isZero/isNegative` | OK, all used names exist | | T3 `Percentage.between` | T7,T11 | zero-whole guard | OK; T7 and T11 both rely on the zero guard for empty portfolios | | T3 `Percentage.of(String)` | T11 `DiversificationLimit` | static factory from string | OK | | T3 `ExchangeRate.convert/difference` | T8 | USD->COP + delta vs base | OK | | T4 `Transaction.netAmount` | T5 | fee direction by trade type | OK; T5 asserts fees reduce sale proceeds | | T4 `InstrumentType.isFund` | T11 | ETF vs equity ceiling | OK | | T5 `Position.fromTransactions` | T6 | validation by replay | OK | | T5 `Position.deriveAll` | T7,T10 | open positions from a history | **DEFECT FOUND — see ruling 1** | | T6 `TransactionRepository.findByTickerAndBroker` | T6 use cases only | per-pair history | OK | | T6 `TimeProvider.today` | T6,T10 | injected clock | OK; no test reads the system clock | | T7 `GetPortfolioUseCase` | T8,T11 | mocked as a collaborator | OK; both mock it rather than its ports | | T7 `PortfolioValuation.totalMarketValue` | T11 | weight denominator | OK | | T9 `SignalSnapshot`, `CombinedScore.from` | T10 | redistribution + `usedSources` | OK; T10 depends on `usedSources().isEmpty()` as the no-data marker, which T9 guarantees | | T10 `SignalProvider`, `SignalSnapshotRepository` | later plans | ports only | OK | | T12 auth ports | later plans | ports only | OK | ### Intra-task self-consistency | Task | Tests vs code vs files | Finding | |---|---|---| | T1 | no tests; verification is `build` + `validateStructure` | OK | | T2 | 9 tests, all against declared methods | Arithmetic corrected in self-review (`297.8542128`) | | T3 | 4 test classes vs 6 created files | OK | | T4 | 6+1 tests vs declared accessors | Arithmetic corrected in self-review (`299.9991133200`) | | T5 | 10 tests vs `fromTransactions` + `deriveAll` | OK after ruling 1 | | T6 | 11 tests vs 3 ports, 4 use cases, 3 exceptions | OK | | T7 | 5 tests vs 3 model types + 1 port + 1 use case | Stub simplified in self-review | | T8 | 6 tests vs 3 model types + 1 port + 1 use case | `sum` helper signature fixed in self-review | | T9 | 18 tests vs 4 model types | Boundary table matches the spec thresholds | | T10 | 7 tests vs 2 ports, 2 model types, 2 use cases | OK after ruling 1 | | T11 | 7 tests vs 3 model types + 1 use case | Compliant-case test corrected in self-review | | T12 | 4 tests vs 3 ports, 2 model types, 1 use case, gradle gate | OK | ### Rulings from the scan Ruling 1: the grouping of transactions by ticker and broker appeared verbatim in three places (T7 `GetPortfolioUseCase`, T10 `GetSignalsDashboardUseCase`, T10 `RefreshSignalSnapshotUseCase`). The review rubric treats verbatim duplication of a logic block as a defect, so the plan as written would have failed review three times. Amended the plan: `Position.deriveAll(List<Transaction>)` is added in T5 as the single owner of position identity, and T7 and T10 call it. Spec section 4.4 places position derivation in the model, so this moves toward the spec rather than away from it. Cost if wrong: `deriveAll` sits in the model where a use case might have been preferred; a one-file move, no behaviour change. Ruling 2: the spec assigns the authentication use case to phase 7 (Plan 2), but a use case with no infrastructure belongs to the domain core. Pulled `AuthenticateUserUseCase` into T12 of this plan; Plan 2 keeps the JWT adapter that implements its ports. Cost if wrong: none; the code is in the right module either way.

3. Task 3: Ruling: the finding is correct and the PLAN TEXT was wrong. The plan's own Ticker source put validation in `of()` with no compact constructor, while Money, Quantity and ExchangeRate all validate canonically. The implementer transcribed the plan faithfully. Amended the plan so Ticker validates in the compact constructor and `of` delegates to it, then sent the finding to the fix loop. Rationale: consistency of invariants across the value objects is what the constraint "value objects are immutable" is protecting, and an unvalidated Ticker would propagate into every ticker-keyed map in Tasks 7, 10 and 11. Cost if wrong: none behaviourally — `of` remains the documented entry point either way.

4. Task 4: Ruling: the concern is real and the PLAN TEXT was at fault — the test was over-specified, coupling itself to an arbitrary validation order, and "fixing" the production code to satisfy it inverted the dependency. Amended the plan: one test asserts only the exception type for a near-empty builder, and a second supplies every field except `type` so the reported name is order-independent. Validation returns to declaration order. Cost if wrong: none behaviourally; only which field name an incomplete builder reports.

5. Task 6: Ruling: the finding is correct and the PLAN TEXT was wrong on two counts — the mixed validation and the missing test. Decided to REJECT identity changes rather than validate both histories: moving a trade to another instrument is a deletion plus a new trade, not an edit, so allowing it would invite exactly this class of bug back. Added InvalidTransactionUpdateException and a full UpdateTransactionUseCaseTest to the plan. Cost if wrong: a user who mistyped a ticker must delete and re-enter instead of editing in place — recoverable, and the stricter rule can be relaxed later without data migration.

6. Task 8: Ruling: the concern is real, the PLAN TEXT was wrong, and `lenient()` is the wrong remedy — it silences the signal that the test is over-stubbing rather than removing the over-stub. Worse, inspecting it exposed a second and deeper plan defect the implementer could not have known about: the test `treatsAPositionWhoseInstrumentIsNotInTheCatalogAsAUsdAsset` placed the position at broker ARQ, which short-circuits to USD_ASSETS *before* the catalog is ever consulted. It passed for the wrong reason and proved nothing about the uncataloged path. Amended the plan: dropped the three unnecessary catalog() calls, and split that test into one that genuinely exercises the fallthrough (uncataloged instrument AT Trii -> TRII_ADR) and one that asserts the short-circuit itself via verifyNoInteractions. No lenient() needed. Cost if wrong: none to production code; the change is confined to test fidelity, and it makes strict stubbing keep working as a real signal on this class.

7. Task 9: Ruling: the finding is correct and the PLAN TEXT was wrong — I wrote `this.scores = scores` in the setter. Fixed by making scores(Map) store a defensive copy. Chose LinkedHashMap over EnumMap for that copy deliberately: EnumMap.put rejects a null key, which would move the null-key case from "dropped at build time" to "throws at builder time" and change observable behaviour. The constructor keeps doing the dropping. Also promoted the Minor null-key case into the fix, because my change to the copy semantics is exactly what could break it — it is regression protection for the fix, not scope creep. Cost if wrong: one extra map allocation per builder use. Negligible.

8. Task 12: Ruling: the per-module gate was the WRONG MEASUREMENT, not a test gap. The uncovered classes are exactly the aggregates (ValuedPosition, TotalReturn, PortfolioValuation, SignalsDashboard, DiversificationReport, ReturnSegment and their builders), which show 0 covered lines in :model's own report even though the :usecase tests construct them — JaCoCo attributes that coverage to the :usecase execution data. The scaffold's own jacocoMergedReport measures 737/759 = 97.1%. Decided to gate on the merged report at 80%, registered at the root project with check depending on it, and to keep per-module reports as diagnostics. A per-module gate would have forced redundant accessor tests in :model written purely to move a number — the habit a coverage gate exists to discourage — and the merged report is already the one the scaffold feeds to Sonar. Also recorded that the merged tasks require --no-configuration-cache, since they read subprojects.sourceSets at execution time and the configuration cache cannot serialize that. Cost if wrong: a merged gate can mask one badly covered module. Not a present risk at 97.1% with :usecase at 100%, and a per-module floor can be added later without moving the gate.

9. Task 12: Ruling: the fix is correct and the defect was mine. Amended the plan to use collectMany and documented why it is required rather than stylistic. Cost if wrong: none; build-script only.

10. ## Final whole-branch review (attempt 4, sonnet) — 1 Critical, 3 Important, 1 Minor CONTROLLER VERIFICATION of the Critical, before acting on it: - Confirmed at GetPortfolioUseCase.java:36-52: the use case takes the FIRST position's currency and reduces all three totals into it with Money::add, which throws CurrencyMismatchException on a mismatch. A portfolio holding both a COP position (BVC via Trii) and a USD position — the exact three-segment shape spec 4.5 requires — crashes instead of valuing. - Found a SECOND face the review only partially saw: EvaluateDiversificationUseCase.java:45-46,72 merges position.marketValue() by ticker and by sector and divides by totalMarketValue(). Same crash, and even without the crash the weights would be meaningless, since they would mix pesos and dollars in one ratio. - Confirmed the blind spot: NO test in :usecase uses Money.cop at all. The test named for the Colombian exchange builds its positions with Money.usd, so the COP path has never executed. Ruling (Critical): the plan never decided how the domain aggregates across currencies. Spec 4.5 says GetPortfolioUseCase "calcula P&L en USD", so the intent was a single presentation currency, but nothing converts. Decided to NORMALIZE AT AGGREGATION: ValuedPosition gains cost and market value expressed in a base currency (USD) alongside its untouched native amounts; GetPortfolioUseCase takes ExchangeRateProvider and fills them; PortfolioValuation's three totals are in the base currency and it records which; EvaluateDiversificationUseCase computes weights from the base-currency amounts. CalculateTotalReturnUseCase keeps reading NATIVE per-position amounts, because separating the currency effect is precisely its job — and its Money.of(amount, currency) relabeling (Important 1) becomes a real conversion instead of a rename. Rejected two alternatives: per-currency total maps (truthful but makes cross-currency diversification weights uncomputable, and the 20/25/30 percent ceilings are portfolio-wide by definition), and scoping GetPortfolio to USD positions only (would strand the BVC segment that CalculateTotalReturnUseCase depends on). Cost if wrong: portfolio totals and diversification weights become rate-dependent, so they move with the TRM even when nothing was traded. That is economically correct for a peso-based investor and it is what spec 4.5 asks for, but it does make those figures less stable than cost-only numbers. Scope: this needs its own task, plan text and review — logged as Task 13, not patched silently.


## Plan 2 — Infraestructura y API

1. Task 1: implemented DONE_WITH_CONCERNS (70/70 :model, 50/50 :usecase (+4), merged coverage 96.92%, commit cad0ad1). Three concerns raised; my assessment of each: (1) No separate red run was captured before implementing. Process deviation, accepted — the new tests do exist and do pass, and the brief's intent (tests written before implementation drives the design) was met in substance. Noted, not escalated. (2) The implementer changed CalculateTotalReturnUseCaseTest's BVC-segment test to build a NATIVE COP position via a new positionCop helper, keeping the pinned assertion of 30 unchanged. Ruling: this is an IMPROVEMENT, not a deviation to tolerate. That test previously built COP-segment positions with Money.usd — it was itself an instance of the exact blind spot this task exists to close. Making it genuinely peso-denominated is what the test always claimed to be doing. Cost if wrong: none; the assertion value did not move. (3) app-service logs UnsatisfiedDependencyException for a missing UserRepository bean. CONTROLLER VERIFICATION: read the scaffold-generated UseCasesConfigTest. It catches that exception and asserts `assertTrue(true, "Unsatisfied dependencies are expected...")` — a tautology that can never fail. Today the warning is legitimate, because Plan 1 defined ports with no implementations on purpose. But the test is worthless as a gate. Ruling: leave it for now (it is scaffold boilerplate and the warning is expected until the adapters land in Task 4), and REPLACE it in Task 10 with a real @SpringBootTest that requires the context to start. Once adapters exist, a context that cannot start is a broken application, and a test that cannot fail would hide exactly that. Added to Task 10's scope. Cost if wrong: a broken wiring would surface at Task 12's smoke check instead of Task 10 — later, but still before anything ships.

2. Task 2: Ruling on concern 1 — THIS WAS MY ERROR, not the implementer's. I ran `git add -A` to commit the plan document while the implementer had uncommitted work in the tree, so 13 code files (compose file, .env.template, application.yml, the baseline migration, and the scaffold sample deletions) landed inside my commit 2ed2f70, whose message is about planning tasks 4-12. Verified: the tree state is correct and all six Gradle modules are on the build; what is wrong is the history attribution, not the code. Decided NOT to rewrite history: the branch is local and unpushed so surgery would be safe, but non-interactive rebase across 30 commits is risk for cosmetic gain, and the working tree — which is what ships — is right. Recorded here instead so the attribution is traceable. The real fix is behavioural: I will scope `git add` to specific paths from now on and never use `git add -A` while an implementer is live. Cost if wrong: `git log --follow` on those 13 files shows a misleading commit subject. No functional cost.

3. Task 2: Ruling on the scaffold sample deletions — the implementer deleted JPARepository, JPARepositoryAdapter, JpaConfig, DBSecret and two of their tests, keeping only AdapterOperations.java. My brief said "leave them for now; Task 4 replaces them", so this is a deviation. Judged CORRECT and kept: JpaConfig and DBSecret are the scaffold's AWS Secrets Manager wiring, which this project does not use (configuration comes from environment variables per the local-first constraint), and leaving them would have meant either an unused AWS dependency on the classpath or a broken context. AdapterOperations is the reusable generic base the real adapters will extend, so keeping exactly that one was the right cut. Cost if wrong: if Task 4 turns out to need something from JpaConfig, it is recoverable from git history at 2ed2f70^.

4. Task 2: Ruling on concern 2 — `spring.main.lazy-initialization: true` was added as a TODO-commented workaround because UseCasesConfig eagerly wires every use case and no gateway has an implementation yet. Accepted as TEMPORARY, with a hard condition: it MUST be removed in Task 4, not Task 10. Reason: lazy initialization means a missing or miswired bean surfaces on the first request instead of at startup — the same failure shape as the tautological UseCasesConfigTest, and it would hide exactly what Task 4's adapters are supposed to prove. Added to Task 4 scope. Cost if wrong: if the adapters in Task 4 do not satisfy every gateway, removing it breaks startup — which is the correct, loud outcome rather than a silent one.

5. Task 2: Ruling (revised) — I accepted `spring.main.lazy-initialization: true` as a contained temporary workaround. The reviewer pointed out a narrower mechanism I had missed: `@ComponentScan(lazyInit = true)` on UseCasesConfig defers only the scanned use-case beans, whereas the global flag disables eager validation for the entire context — JPA repositories, Flyway, the RestClient, the CORS and security filters, all of it. That is precisely the failure shape my own hard condition was meant to prevent, so my acceptance was too permissive. REVISED DECISION: narrow it NOW, in a Task 2 fix round, rather than carrying it to Task 4. The reason not to defer: Task 3 builds the real schema and Task 4 builds the adapters, and both would be developed against a context that does not validate its wiring at startup. Fixing it now costs one line; fixing it later means two tasks of work were never eagerly validated. Cost if wrong: if @ComponentScan(lazyInit = true) does not defer enough, startup fails loudly at the next boot — which is the correct outcome and is caught immediately.

6. Task 4: Ruling on the Important finding — the reviewer and I independently reached the same conclusion, and it recommended the third option my own brief had floated. Reusing UnknownInstrumentException for a broker miss breaks this codebase's one-exception-per-failure convention and, more concretely, would make Task 9 emit an RFC 7807 problem typed "unknown instrument" for a bad BROKER name — telling a client something untrue even though the message is right. DECISION: rename to UnknownCatalogEntryException rather than adding a sibling type. One honest type beats two, because the domain treats both conditions identically (reject the write) and a client gains nothing from separate problem types when the message already names which entry was missing. The private-String-constructor-plus-factory shape also disappears. Doing it NOW, not after Task 9: renaming before the exception-to-ProblemDetail mapping exists costs a rename; renaming after costs a rename plus a mapping change plus its tests. Cost if wrong: if a client later needs to distinguish a missing instrument from a missing broker programmatically, a subtype can be added without changing the mapping.

7. Task 5: Ruling on concern 1 — saveAll issues one Spring Data batch but performs an existence-check SELECT per snapshot first, so a refresh of 20 instruments costs 20 SELECTs plus one batch write. Accepted: this job runs once a day over roughly 20 rows, so the cost is negligible and a native ON CONFLICT upsert would trade readable code for an untyped query. Recorded as a deferred minor WITH the concrete fix, so it is actionable rather than vague: the unique key is (instrument_id, snapshot_date), so a single findByInstrumentIdInAndSnapshotDate would collapse the N SELECTs into one without leaving Spring Data. Revisit if the instrument count grows or the job starts running intraday.

8. Task 5: Ruling on concern 2 — two tests compared BigDecimal with equals-based .contains() against NUMERIC(5,2) values read back at scale 2, and failed. Fixed with isEqualByComparingTo, test-only. Correct fix. But it exposes a latent design inconsistency worth the final review's attention: Money and Quantity normalize their scale precisely so equals is reliable, while the score map holds raw BigDecimal, so a score is scale 0 in memory and scale 2 after a round trip. Nothing in production compares scores by equals today, and CombinedScore does arithmetic where scale does not change the result, so this is not a defect now — but it is an inconsistency that could bite the first code that compares a stored score to a computed one.

9. Task 7: Ruling made BEFORE dispatch, on a loose end in my own plan. The plan's Task 7 entry says the AI synthesis adapter "falls back to the last persisted snapshot rather than failing the request". That requires an AiSynthesisRepository port and a JPA adapter, neither of which exists — and Task 3 did create an `ai_syntheses` table from the spec's full data model. DECISION: Task 7 delivers the four providers and four use cases only, degrading on failure the same way the signal providers do (absent data reported as absent, never as a zero or a stale value silently presented as current). No persistence fallback, no new repository port. Reasons: a stale AI narrative presented as today's analysis is worse than an honest gap, since the user cannot tell it is stale; adding a repository here duplicates what Task 10's scheduled refresh should own; and the plan's own degradation rule everywhere else is "absent means absent". The `ai_syntheses` table therefore stays UNUSED at the end of Plan 2 — a deliberate, documented gap rather than an oversight, to be filled when a task actually needs to persist narratives. Cost if wrong: one unused table, and a cold /sintesis-ia call returns no data instead of yesterday's; recoverable by adding the port later without schema change.

10. Task 8: two rulings made BEFORE dispatch, both departing from my own plan text. Ruling A — module placement. The plan says "a security-jwt adapter" without saying where the filter chain goes. Decided: the two PORT IMPLEMENTATIONS (PasswordHasher, TokenProvider) go in a new infrastructure/driven-adapters/security-jwt module, because the domain calls them — they are driven adapters. The Spring Security filter chain and CORS configuration go in the EXISTING api-rest entry-point module, because they intercept inbound requests. Putting the port implementations in api-rest would invert the direction of two of the four dependencies and muddle which side calls which. Cost if wrong: one extra Gradle module; the split is cheap to collapse later. Ruling B — library. The plan mentions jjwt. Decided to use Spring Security's Nimbus-based JwtEncoder and JwtDecoder instead. Three reasons: jjwt is not in Spring Boot's dependency management so it would need a pinned version this project has otherwise avoided; nimbus-jose-jwt arrives managed via spring-boot-starter-oauth2-resource-server; and the resource-server filter chain already does bearer token extraction and validation, so we write configuration rather than a custom OncePerRequestFilter that has to get token parsing right by hand. Less of our own security-critical code is the point. Cost if wrong: if Nimbus proves awkward for HS256 with a shared secret, jjwt is a contained swap behind the TokenProvider port — which is exactly why the port exists.

11. Task 9: Ruling on /fundamentals — accepting the reviewer's recommendation over my own framing. I had floated "200 with an empty body" as an option; the reviewer argued against collapsing it into the existing hasData:false 200 path because that models a DIFFERENT state (the provider answered and said it has no data, versus the ticker not being resolvable at all). That distinction is real and I had missed it. Going with a ProblemDetail 404 via a small exception type, so the API has exactly one error mechanism.

12. Task 12: Ruling — folding Task 12's task review into the Plan 2 FINAL whole-branch review rather than running a separate pass. Reasons: its surface is infrastructure only (Dockerfile, compose service, shell script, README prose) with no domain or application code; the final review covers the same diff anyway; and I have first-hand evidence the deliverable works because I executed it myself rather than trusting a report. The final review is instructed to cover Task 12 explicitly so nothing is skipped. Cost if wrong: an infrastructure defect gets one review pass instead of two. Mitigated by the fact that the deliverable is empirically verified end to end.

13. RULING AUDIT — the reviewer disagreed with four of my decisions. My response to each: (a) USD normalization: I accept the diagnosis is sharper than my framing, but I do NOT accept the proposed fix as stated. Converting the cost leg at the base rate would make totalInvested stable, but it would then fold the currency effect INTO totalUnrealizedPnl (market@today - cost@base = asset gain + currency effect), which is the very conflation being complained about, just relocated. Converting both legs at today's rate, as the code does, yields an asset-only P&L — the cleaner separation — at the cost of an unstable "invested" figure. Both readings are defensible and they produce visibly different numbers on the user's dashboard, so this is a product decision, not mine to take unilaterally. Putting it to the user. (b) The TRM fallback contradiction: accepted entirely. See the Critical above. (c) Folding Task 12's review: accepted. The reviewer found two real Dockerfile defects, neither on the happy path, so my "I ran it myself" proved the deliverable works and not that the artefact is sound. Those are two different claims and I conflated them. (d) The Task 11 re-reviewer's "findLatest returns one row so it needs no entity graph": accepted as wrong criterion, and it let a second N+1 through in /signals at 1+2N queries. Reviewer agreed with: the exception rename, open-in-view false plus no-MockMvc (proportionate, and correctly scoped so spec 6.1's MockMvc mandate for api-rest still holds), Nimbus over jjwt, not rewriting history after the git add -A contamination, and the AI fallback ruling as a principle. Fix round dispatched for the Critical and the four Importants. The (a) question goes to the user.

14. USER DECISIONS (asked, not ruled) — both settle questions I had deliberately not taken alone: 1. Portfolio cost leg: KEEP both legs at today's TRM. The user chose the current behaviour, so /portfolio reports asset-only P&L and /portfolio/returns remains the single place the currency effect is broken out, at the cost of an "invested" figure that moves with the rate. This confirms my pushback against the final review's proposed fix: converting the cost leg at the base rate would have folded the currency effect into /portfolio's P&L — the same conflation, relocated. No change to GetPortfolioUseCase's conversion semantics. 2. TRM fallback: REPORT THE ABSENCE. Option (A) is now a settled decision rather than my preference — no rate means /portfolio/returns says the currency effect is unavailable and peso positions come back unpriced, reusing the ValuedPosition.priced concept the codebase already has. Consistent with how absent signal sources and missing quotes are already handled, and it makes the "absent means absent" rule actually uniform across the application instead of holding in three places and failing in the fourth.


## Where the signal-refresh overlap guard lives once a button can also trigger it (Task 13)

**Context.** `RefreshSignalSnapshotUseCase.execute()` must never run concurrently with itself —
two overlapping runs write the same day's snapshot rows. Today the guard is an `AtomicBoolean` in
`SignalSnapshotSchedulerConfig` (module `applications/app-service`), which protects the cron and
nothing else. Task 13 adds `POST /api/v1/signals/refresh`, a second caller, in
`SignalController` (module `infrastructure/entry-points/api-rest`).

**The trap.** Giving the controller its own `AtomicBoolean` looks like "the same guard" but provides
**zero** mutual exclusion between the two callers: two independent booleans cannot exclude each
other, so the cron and a button press can still overlap. The guard must be one shared instance.

**Why it cannot be a new component in app-service.** `api-rest` does not depend on `app-service`
— the dependency runs the other way, with app-service as the composition root. A coordinator bean
placed there is invisible to the controller.

**Ruling.** Move the guard *into* `RefreshSignalSnapshotUseCase` (module `domain/usecase`), which
both callers already inject and which Spring wires as a single bean, so the two callers share one
guard structurally rather than by discipline. `AtomicBoolean` is plain `java.util.concurrent`, so
the domain's zero-framework rule is not broken. This is also the better home on the merits: "this
operation must not run concurrently with itself" is a property of the operation, not of its
callers — the current placement leaves the invariant enforced for one caller and unenforced for
any future one, which is precisely the bug Task 13 would otherwise introduce.

**The domain must not know about HTTP**, so `execute()` returns an outcome the callers map
themselves: a count when it ran, a distinct "already running" result when the guard refused.
- cron: on refusal, log a warning and skip — current behaviour, preserved.
- endpoint: on refusal, **HTTP 409 Conflict** with a `ProblemDetail`. It must NOT return 200: a 200
  tells the user a refresh happened when none did, and the figures they then read are the old ones.

**Cost if wrong.** The guard sits one module deeper than strictly needed. Cheap to move back, and
far cheaper than the overlapping-writes race the duplicated-boolean alternative would ship.

## Rendering a weight bar against its ceiling without dividing (Task 10)

Task 10's brief asks for "a bar per weight against a ceiling line", which is the same shape that
produced a contradiction in Task 7: sizing a bar *proportionally to another quantity* requires
dividing one by the other, which non-negotiable #1 forbids in the browser.

**Ruling: a fixed 0-100% track, where both the weight and its ceiling are positioned by their own
percentage strings handed straight to CSS.** The API returns weights already as percentages
(`weightByTicker` values like `'3.4417'`), so `width: 3.4417%` and a ceiling marker at
`left: 20%` are literal string interpolations into a style — CSS does the parsing, and no
JavaScript arithmetic touches either number. Nothing is divided, nothing is coerced.

What this forbids: normalizing the track so the ceiling always sits at a fixed position (say 80% of
the width). That *is* actual/allowed division and is not allowed. The cost is that a 3.44% weight
against a 20% ceiling renders as a short bar in a mostly-empty track — which is the honest picture
of a portfolio nowhere near its limit, not a defect to design away.

**Violation state comes from the API's `violations` array and `compliant` flag only.** Do not invent
a "approaching the limit" warning tier: the backend defines no such threshold, and inventing one
client-side makes up a business rule. (`compareDecimalStrings` is string-wise and would be
*mechanically* safe to compare with — the objection here is inventing the rule, not the arithmetic.)

## The AI synthesis average is live proof of a zero that must not be displayed (Task 11)

Checked against the running backend: `/api/v1/ai-synthesis` currently returns `withData: []`, all
15 tickers in `withoutData`, and **`averageScore: "0.00"`**. The average is computed over zero
scored tickers and is meaningless.

**Ruling: when `withData` is empty, the UI must not render `averageScore` as a figure at all.** It
states that no ticker has AI data and that no average can be computed. Rendering "0.00" tells the
user their portfolio scores zero out of ten — the same family of defect as the fabricated TRM
(Plan 2) and the `0.00`/`STRONG_SELL` rows in the signals table (Task 8), and the one this project
keeps re-encountering: **a zero that means "absent" displayed as a zero that means "measured".**
This is not a hypothetical edge case; it is the app's behaviour against live data today, so the
test for it asserts against the real shape and must not permit a bare `0` to reach the DOM.

Where `withData` is non-empty, the average must still be labelled as covering only those tickers,
with the `withoutData` ones listed as explicitly excluded.

## Live contract facts for Task 11, confirmed against the running backend

- `/api/v1/earnings` returns `[]`. The earnings view renders its empty state against live data, so
  "ordered by proximity" cannot be confirmed live and must be proven by fixture.
- `/api/v1/watchlist` returns 31 items with `price: null` and `technicalScore: null` on at least the
  first, so null-handling is the common path here, not the edge.
- **The watchlist's `signal` values are Spanish (`"COMPRA"`)**, a different vocabulary from the
  `BUY`/`HOLD`/`STRONG_SELL` enum used elsewhere in the contract. Render what the API sends; do NOT
  map it into the other vocabulary to make the two views look uniform. If the schema does not
  already accommodate this, report the mismatch rather than widening the schema silently.

## Two latent Criticals in the API layer, found before Task 11 wrote a line

Verified against the running backend. Both are Zod schemas from Task 3 declaring fields as
`z.string()` that the API genuinely returns as `null`, so the schema REJECTS live responses and the
view throws instead of rendering.

**1. `frontend/src/api/schemas/fundamentals.ts` — breaks on every ticker.**
13 metric fields are `z.string()`. Live data:
- `NU` (a stock, `hasData: true`): `revenueCagr5y`, `epsCagr5y`, `dividendYield` all null.
- `QQQ` (an ETF, `hasData: true`): **11 of 13 metrics null** — only `beta` and the flags present.
Fundamentals cannot work for a stock OR a fund as written.

**2. `frontend/src/api/schemas/watchlist.ts` — breaks on 39% of rows.**
`price` and `technicalScore` are `z.string()`; **12 of 31 live items** return null for both. The
backend DTO's own Javadoc says "Several fields are frequently absent upstream", so the nulls are
documented, intended behaviour. The schema is wrong, not the API.

**Ruling: add `.nullable()` to exactly these fields.** This is the *correct* kind of widening —
making the schema match a documented contract. It is NOT the forbidden kind, which is loosening a
schema so the UI can stop handling a case the API really does send. The distinction is the test:
does the API emit this shape? Here, demonstrably yes.

Note which schemas got this right: `returns.ts` has 4 nullable fields and `TotalReturnResponse.java`
is the other DTO documented as having absent fields. Whoever wrote `returns.ts` read the backend;
whoever wrote `watchlist.ts` and `fundamentals.ts` did not. `signals.ts`, `portfolio.ts`,
`diversification.ts` and `earnings.ts` declare zero nullable fields, and I confirmed `/signals` and
`/portfolio` currently return no nulls — they are fine *today*, which is not the same as correct.

## `hasData: true` does not mean the metrics are there (Task 11)

`QQQ` returns `hasData: true` with 11 of 13 metrics null. So the no-data state cannot be driven by
the `hasData` flag alone: it must be **per metric**. Rendering a P/E grid for an ETF because
`hasData` was true produces a grid of blanks — which is exactly the "do not render an empty P/E grid
for a fund" failure the brief warned about, now confirmed with live data.

## The backend leaks float artifacts into fundamentals strings (Task 11)

Live values include `roe: "30.259999999999998"`, `peg: "0.3662433623319399"`,
`beta: "0.96430856"` — a double's decimal expansion serialized into the contract's string. The
frontend must not display these raw; route every fundamentals metric through
`formatDecimalString` so the user sees `30.26`, not `30.259999999999998`. The frontend must NOT
"fix" the value by computing on it — formatting a string for display is string truncation, which is
sanctioned; arithmetic is not. The upstream contamination is a backend finding, recorded here and
reported to the user, not patched from the browser.
