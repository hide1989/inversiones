# Spec de migración — Plataforma de Inversiones

**Fecha:** 2026-09-29
**Autor:** David
**Estado:** aprobado, pendiente de ejecución
**Reemplaza:** prototipo `portafolio-dashboard/index.html` + n8n en `/Users/David/Documents/Claude/Projects/Inversiones`

---

## 1. Contexto y objetivo

Hoy existe un prototipo funcional: 20 workflows de n8n en un VPS Hostinger que ingestan datos de
mercado (Finnhub, FMP, Yahoo Finance, ApeWisdom, StockTwits, CNN Fear & Greed, MiniMax M3, IBKR Flex)
y un único archivo `index.html` de 1.546 líneas que contiene el portafolio hardcodeado, la lógica de
cálculo de P&L, el scoring combinado de señales y todo el renderizado.

Limitaciones del prototipo:

- El portafolio vive en constantes JavaScript (`PORT_USD`, `TRII_USD`, `PORT_BVC`): cambiarlo exige editar código.
- La lógica de negocio (`calcCombinedScores`, `renderResumenTotal`, costo promedio, efecto cambiario)
  está en el navegador, mezclada con el DOM, y no tiene una sola prueba.
- El navegador llama a n8n directamente: la URL del VPS y la superficie de sus webhooks quedan expuestas.
- No hay histórico: cada recarga muestra solo el estado del día.
- No hay despliegue reproducible ni pipeline.

**Objetivo:** migrar a un backend Java 25 con arquitectura limpia y un frontend React SPA, con pruebas
unitarias y de aceptación en ambos, desplegados por un pipeline de Jenkins.

### Alcance

Dentro del alcance:

- Backend Java 25 / Spring Boot 4 con arquitectura limpia (scaffold de Bancolombia).
- Frontend SPA en React 19 + TypeScript, rediseñado.
- Persistencia PostgreSQL con JPA/Hibernate y migraciones Flyway.
- Autenticación JWT de un solo usuario.
- CRUD de transacciones con derivación de posiciones.
- Migración al dominio Java de todo el cálculo que hoy vive en JavaScript.
- Persistencia de snapshots históricos de señales.
- Pruebas unitarias (JUnit 5 + Mockito / Vitest) y de aceptación (Cucumber JVM / Playwright).
- Pipeline de Jenkins ejecutable en local y en el VPS.
- Despliegue: frontend en Vercel, backend y base de datos en el VPS Hostinger.

Fuera del alcance:

- Reimplementar los workflows de n8n. Los 20 siguen siendo la capa de ingesta; el backend los consume.
- Órdenes o movimientos de fondos. El acceso a IBKR es y sigue siendo de solo lectura.
- Multiusuario, roles o registro público.
- Aplicación móvil nativa.

---

## 2. Decisiones de arquitectura

Cada decisión queda registrada como ADR en `docs/adr/`.

### ADR-001 · PostgreSQL con JPA/Hibernate, no MongoDB

El requisito inicial pedía MongoDB **y** JPA como ORM. Son incompatibles: JPA es un estándar para
bases relacionales y no existe una implementación viable para MongoDB (Hibernate OGM está abandonado).

Se elige **PostgreSQL + JPA/Hibernate**. Los datos del portafolio son inherentemente relacionales
(transacciones que pertenecen a instrumentos y brókers, tasas de cambio con serie temporal, cálculos
de costo promedio ponderado y agregaciones de P&L). El *driven adapter* `jpa` del scaffold es de
primera clase, y Testcontainers permite probar contra un Postgres real.

### ADR-002 · El backend Java no puede vivir en Vercel

Los runtimes de Vercel Functions son Node.js, Python, Go, Ruby, Rust, Bun, WebAssembly y Edge.
No hay runtime Java, ni oficial ni comunitario (https://vercel.com/docs/functions/runtimes).

Decisión: **frontend en Vercel** (build estático, CDN global, capa gratuita) y **backend + Postgres en
el VPS Hostinger existente** con Docker, junto a n8n. Costo adicional cero, sin arranques en frío, y
latencia mínima hacia n8n por estar en la misma máquina.

Alternativas descartadas: Render free tier (512 MB de RAM y suspensión a los 15 min con arranque en
frío de 30–60 s), Google Cloud Run (free tier generoso pero exige cuenta GCP con tarjeta y complica el
pipeline con `gcloud auth` y Artifact Registry), Fly.io (ya no tiene capa gratuita real).

### ADR-003 · Estilo imperativo, no reactivo

El scaffold ofrece `--type=imperative` y `--type=reactive`. JPA/Hibernate es bloqueante; combinarlo
con WebFlux bloquea el event loop. Se usa **imperativo** con el entry point `restmvc`. Con máquinas
virtuales de Java 25 (`spring.threads.virtual.enabled=true`) el modelo imperativo escala de sobra para
esta carga.

### ADR-004 · n8n permanece como capa de ingesta

Los 20 workflows funcionan, tienen caché en `staticData` y resuelven rate limits de ocho APIs externas.
Reimplementarlos duplicaría trabajo sin beneficio. El backend los consume vía `rest-consumer` y aporta
lo que n8n no da: dominio probado, persistencia, histórico y una sola frontera de seguridad.

### ADR-005 · Java 25 con Spring Boot 4.0

Spring Boot 4.0 (noviembre 2025) tiene soporte de primera clase para Java 25 manteniendo Java 17 como
baseline. Riesgo conocido: el plugin de Bancolombia puede no aceptar aún `--javaVersion=VERSION_25`;
ver mitigación en la fase 0.

---

## 3. Estructura del repositorio

Monorepo nuevo en `/Users/David/PASCUAL BRAVO/PRUEBAS DE SOFTWARE/inversiones`.

```
inversiones/
├── spec.md                       ← este documento
├── README.md                     ← en inglés
├── .gitignore
├── Jenkinsfile
├── docs/
│   └── adr/                      ← ADR-001 … ADR-005
├── backend/                      ← Gradle multi-módulo
├── frontend/                     ← Vite + React 19 + TypeScript
├── deploy/
│   ├── docker-compose.prod.yml
│   ├── nginx/inversiones.conf
│   └── .env.template
└── jenkins/
    ├── docker-compose.yml
    ├── casc/jenkins.yaml         ← Configuration as Code
    └── plugins.txt
```

Convenciones (obligatorias en todas las fases):

- **Commits:** Conventional Commits en inglés. `feat(domain): add weighted average cost calculation`,
  `test(usecase): cover proportional score redistribution`, `ci(jenkins): parameterize deploy target`.
- **Documentación del código:** Javadoc y TSDoc en inglés. Identificadores en inglés.
- **Este spec y los ADR:** en español, por ser documentos de planeación.
- **Idioma de la interfaz:** español.

---

## 4. Backend

### 4.1 Generación con el scaffold

```bash
mkdir -p backend && cd backend
gradle init --type basic --dsl groovy --project-name inversiones
# plugin co.com.bancolombia.cleanArchitecture en build.gradle

gradle ca \
  --package=co.com.david.inversiones \
  --type=imperative \
  --name=inversiones \
  --javaVersion=VERSION_25 \
  --lombok=true \
  --metrics=true

gradle gda --type=jpa
gradle gda --type=restconsumer
gradle gep --type=restmvc
gradle gm  --name=Transaction
gradle guc --name=RegisterTransaction
```

### 4.2 Módulos

```
backend/
├── applications/app-service/          ← Spring Boot main, wiring de DI, properties
├── domain/
│   ├── model/                         ← entidades, value objects, PUERTOS. Sin frameworks
│   └── usecase/                       ← lógica de negocio. Sin frameworks
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── jpa-repository/            ← PostgreSQL, Hibernate, Flyway
│   │   ├── rest-consumer/             ← cliente HTTP hacia n8n
│   │   └── security-jwt/              ← emisión y validación de tokens
│   ├── entry-points/api-rest/         ← controladores, OpenAPI, manejo de errores
│   └── helpers/
└── deployment/Dockerfile
```

La regla de dependencia la impone el scaffold: `domain` no compila contra Spring ni contra JPA.
`gradle validateStructure` lo verifica en el pipeline.

### 4.3 Patrones obligatorios

- **Builder** en todas las construcciones de objetos. En `domain/model`, builders escritos a mano para
  no acoplar el dominio a Lombok; en infraestructura, `@Builder` de Lombok.
- **Inyección de dependencias por constructor.** Sin `@Autowired` en campos. Los casos de uso reciben
  puertos, no implementaciones; el wiring vive en `app-service`.
- **Inmutabilidad** en value objects (`record` de Java donde aplique).
- **Puertos y adaptadores:** el dominio define la interfaz, la infraestructura la implementa.

### 4.4 Modelo de dominio (`domain/model`)

Value objects:

| Tipo | Contenido |
|---|---|
| `Ticker` | símbolo normalizado, validado |
| `Money` | `BigDecimal` + `Currency` (USD, COP); aritmética que rechaza mezclar divisas |
| `Quantity` | `BigDecimal` con soporte de fracciones de acción |
| `ExchangeRate` | TRM COP/USD con fecha |
| `Percentage` | valor y formateo |

Entidades y agregados:

| Tipo | Responsabilidad |
|---|---|
| `Instrument` | ticker, nombre, `InstrumentType` (EQUITY, ETF, ADR), sector, bolsa, divisa |
| `Broker` | Hapi, ARQ, Trii |
| `Transaction` | id, instrumento, bróker, `TransactionType` (BUY, SELL), cantidad, precio unitario, comisiones, divisa, fecha |
| `Position` | derivada de transacciones: cantidad, costo promedio ponderado, costo total |
| `Portfolio` | agregado raíz: posiciones + métricas |
| `Quote` | precio actual, variación del día |
| `SignalSnapshot` | por ticker: `technicalScore`, `newsScore`, `analystScore`, `insiderScore`, `socialScore`, `wsbScore` |
| `CombinedScore` | score total 1–10 + `SignalLevel` |
| `SignalLevel` | STRONG_BUY ≥8.0, BUY 6.5–7.9, HOLD 4.5–6.4, SELL 3.0–4.4, STRONG_SELL <3.0 |
| `AiSynthesis` | score IA, señal, convicción, horizonte, resumen, catalizadores, riesgos |
| `EarningsEvent` | fecha, días restantes, EPS estimado, sorpresa histórica |
| `WatchlistItem` | ticker, precio, score técnico, score WSB, señal |
| `TotalReturn` | P&L por segmento + efecto cambiario |
| `DiversificationReport` | infracciones de las reglas de asignación |
| `User` | credenciales con hash |

Puertos (interfaces en `domain/model/gateways`):

```
TransactionRepository      InstrumentRepository     UserRepository
SignalSnapshotRepository   ExchangeRateRepository
QuoteProvider              ExchangeRateProvider     SignalProvider
AiSynthesisProvider        EarningsProvider         WatchlistProvider
FundamentalsProvider       TokenProvider            PasswordHasher
TimeProvider
```

`TimeProvider` existe para que ninguna prueba dependa del reloj del sistema.

### 4.5 Casos de uso (`domain/usecase`)

| Caso de uso | Regla de negocio que encapsula |
|---|---|
| `RegisterTransactionUseCase` | valida el lote, rechaza ventas mayores a la posición, recalcula costo promedio ponderado |
| `UpdateTransactionUseCase` | edición con revalidación del histórico |
| `DeleteTransactionUseCase` | borrado con recálculo de la posición |
| `ListTransactionsUseCase` | filtrado por ticker, bróker y rango de fechas |
| `GetPortfolioUseCase` | deriva posiciones desde transacciones, cruza con `QuoteProvider`, calcula P&L en USD |
| `CalculateTotalReturnUseCase` | P&L en COP separando **efecto cambiario** de ganancia de activos, en 3 segmentos (activos USD, Trii ADR NYSE, BVC Colombia). Efecto cambiario = USD invertido × (TRM actual − TRM base) |
| `CalculateCombinedScoreUseCase` | pesos técnico 0.30, noticias 0.20, analistas 0.15, insider 0.15, social 0.10, WSB 0.10, con **redistribución proporcional** cuando faltan fuentes |
| `GetSignalsDashboardUseCase` | consolida los seis proveedores y ordena por score total |
| `GetSignalHistoryUseCase` | serie temporal de snapshots persistidos |
| `GetAiSynthesisUseCase` | síntesis de MiniMax vía n8n, excluyendo tickers marcados `sin_datos` |
| `GetEarningsUseCase` | calendario ordenado por proximidad |
| `GetWatchlistUseCase` | watchlist externa + tickers WSB en tendencia |
| `GetFundamentalsUseCase` | múltiplos a demanda por ticker |
| `EvaluateDiversificationUseCase` | máx. 20% por ticker, 25% por ETF, 30% por sector; núcleo ETF 50–60%, satélites 20–30%, cobertura 15–25% |
| `AuthenticateUserUseCase` | verifica credenciales y emite JWT |
| `RefreshSignalSnapshotUseCase` | job programado: consulta n8n y persiste el snapshot del día |

Todos se prueban con **JUnit 5 + Mockito** sin contexto de Spring. Es el núcleo de la cobertura.

### 4.6 API REST (`/api/v1`)

| Método y ruta | Descripción | Auth |
|---|---|---|
| `POST /auth/login` | credenciales → JWT | no |
| `GET /portfolio` | posiciones, valor actual, P&L USD | sí |
| `GET /portfolio/returns` | retorno total en COP con efecto cambiario | sí |
| `GET /transactions` | listado con filtros | sí |
| `POST /transactions` | registrar compra o venta | sí |
| `PUT /transactions/{id}` | editar | sí |
| `DELETE /transactions/{id}` | eliminar | sí |
| `GET /signals` | tabla de señales con score combinado | sí |
| `GET /signals/history?ticker=&from=&to=` | histórico | sí |
| `GET /ai-synthesis` | síntesis IA por ticker | sí |
| `GET /earnings` | calendario | sí |
| `GET /watchlist` | watchlist externa | sí |
| `GET /fundamentals/{ticker}` | múltiplos a demanda | sí |
| `GET /diversification` | cumplimiento de reglas | sí |
| `GET /actuator/health` | liveness y readiness | no |

Errores con `ProblemDetail` (RFC 7807). OpenAPI 3.1 generado con springdoc y publicado en `/swagger-ui`.
Los DTO de la API son propios del entry point; las entidades JPA y las del dominio nunca se serializan.

### 4.7 Esquema de base de datos (Flyway)

```
users             (id, username, password_hash, created_at)
brokers           (id, name)
instruments       (id, ticker, name, type, sector, exchange, currency)
transactions      (id, instrument_id, broker_id, type, quantity, unit_price,
                   fees, currency, traded_at, created_at)
exchange_rates    (id, base_currency, quote_currency, rate, rate_date)
signal_snapshots  (id, instrument_id, snapshot_date, technical_score, news_score,
                   analyst_score, insider_score, social_score, wsb_score,
                   combined_score, signal_level, raw_payload jsonb)
ai_syntheses      (id, instrument_id, snapshot_date, ia_score, signal, conviction,
                   horizon, summary, catalysts, risks)
```

Índices en `transactions(instrument_id, traded_at)`, `signal_snapshots(instrument_id, snapshot_date)`
y único en `instruments(ticker)`. `V1__initial_schema.sql` crea el esquema;
`V2__seed_reference_data.sql` siembra brókers, instrumentos y el usuario inicial;
`V3__seed_current_portfolio.sql` migra las transacciones del portafolio actual desde
`PORT_USD`, `TRII_USD` y `PORT_BVC`, con `TRM_BASE = 3568.42`.

### 4.8 Adaptador de n8n

`rest-consumer` con `RestClient` de Spring 6, un método por endpoint:

```
/inv/precios-hoy  /inv/trm-hoy  /inv/bvc-precios  /inv/ratings-analistas
/inv/indicadores-tecnicos  /inv/earnings  /inv/stocktwits  /inv/reddit
/inv/news-sentiment  /inv/sintesis-ia  /inv/watchlist  /inv/insider-trading
/inv/ibkr-portfolio  /inv/analizar-empresa  /inv/etf-ficha
```

Con timeout de conexión 5 s y de lectura 30 s (la síntesis IA tarda ~55 s y se sirve desde caché de n8n),
**Resilience4j** con circuit breaker y reintentos, caché local con Caffeine y TTL por endpoint, y
degradación explícita: si una fuente falla, el score se recalcula con las restantes en lugar de
romper la respuesta completa. La URL base y las claves llegan por variables de entorno.

---

## 5. Frontend

### 5.1 Stack

Vite 6, React 19, TypeScript en modo estricto, Tailwind CSS 4, TanStack Query (caché de servidor y
revalidación), React Router 7, React Hook Form + Zod (formularios y validación de respuestas),
Recharts (gráficas).

### 5.2 Dirección visual: terminal financiero

Los datos mandan y el cromo desaparece.

- Fondo casi negro (`#0A0B0D`) con superficies elevadas por capas, no por bordes.
- Acento ámbar para lo accionable, verde y rojo semánticos solo para signo de variación,
  nunca como único portador de información (se acompaña de ▲ ▼ y signo).
- **Todas las cifras en mono tabular** (`IBM Plex Mono`, `font-variant-numeric: tabular-nums`) para
  que las columnas se alineen; `Inter` para texto.
- Densidad alta: la tabla de señales tiene 15 columnas y debe leerse sin scroll horizontal en 1440 px.
- Barras de score inline en vez de badges de color, para comparar de un vistazo.
- Tokens de diseño en CSS custom properties; contraste mínimo AA (4.5:1) verificado.

### 5.3 Vistas

| Ruta | Contenido |
|---|---|
| `/login` | usuario y contraseña |
| `/posiciones` | tarjetas de resumen, tabla NYSE/NASDAQ, tabla Trii COP, resumen total de retorno con efecto cambiario |
| `/senales` | tarjetas de resumen, tabla ordenable con score combinado, noticias clave |
| `/earnings` | grid de próximos reportes |
| `/sintesis-ia` | tarjetas por ticker con score, convicción, catalizadores y riesgos |
| `/watchlist` | watchlist externa y tendencias WSB |
| `/transacciones` | **nuevo:** CRUD de compras y ventas por lote |
| `/diversificacion` | cumplimiento de las reglas de asignación |

Estados de carga con skeletons, estados de error accionables con reintento, y estados vacíos explícitos.

---

## 6. Estrategia de pruebas

Meta: **80% de cobertura de líneas** medida con JaCoCo, verificada como gate del pipeline.
Exclusiones permitidas: clases de configuración, DTO generados y `main`.

### 6.1 Backend

| Capa | Herramientas y enfoque |
|---|---|
| `domain/model` | JUnit 5. Value objects: validación, igualdad, aritmética de `Money` que rechaza mezcla de divisas |
| `domain/usecase` | **JUnit 5 + Mockito.** Puertos mockeados, sin Spring. Casos límite: posición cero, venta en descubierto, fuentes de señal ausentes y redistribución de pesos, TRM faltante, división por cero en upside |
| `jpa-repository` | `@DataJpaTest` + **Testcontainers** con PostgreSQL real. Migraciones Flyway aplicadas en la prueba. Mappers dominio↔entidad en ambos sentidos |
| `rest-consumer` | **MockWebServer** con payloads reales de n8n como fixtures en `src/test/resources/n8n/`. Casos: 200, 500, timeout, JSON malformado, apertura del circuit breaker |
| `security-jwt` | firma, validación, expiración, token manipulado |
| `api-rest` | `@SpringBootTest` + MockMvc. Códigos de estado, forma del JSON, `ProblemDetail`, rutas protegidas sin token → 401 |

### 6.2 Aceptación del backend

**Cucumber JVM** con escenarios Gherkin en inglés, contra la aplicación completamente cableada:
Testcontainers para PostgreSQL y **WireMock** simulando los endpoints de n8n con payloads fijos.

```gherkin
Feature: Portfolio profit and loss

  Scenario: Exchange rate effect is reported separately from asset gains
    Given the user has bought 0.486372 shares of QQQ for 300.00 USD
    And the reference exchange rate was 3568.42 COP per USD
    And the current exchange rate is 3912.40 COP per USD
    When the user requests the total return report
    Then the asset gain and the exchange rate effect are reported as separate amounts

  Scenario: Combined score redistributes weights when a source is unavailable
    Given technical, news and analyst signals are available for TSLA
    And the insider, social and WSB sources are unavailable
    When the user requests the signals dashboard
    Then the combined score for TSLA is computed from the three available sources
    And their weights are redistributed proportionally
```

Escenarios cubiertos: autenticación, ciclo completo de transacciones, P&L con efecto cambiario,
scoring combinado con fuentes ausentes, reglas de diversificación, degradación ante caída de n8n.

### 6.3 Frontend

- **Vitest + React Testing Library** para componentes y hooks; **MSW** para simular la API.
  Se prueban formato de moneda, signo y color de variación, ordenamiento de la tabla de señales,
  validación del formulario de transacciones y estados de carga, error y vacío.
- **Playwright** para aceptación de punta a punta contra el stack levantado con Docker Compose:
  page objects por vista, fixture de autenticación que reutiliza el estado de sesión, ejecución en
  Chromium y WebKit, trazas y capturas solo en fallo, reporte HTML publicado en Jenkins.
  Flujos: (1) login y logout, (2) carga del dashboard de posiciones, (3) ordenar la tabla de señales,
  (4) registrar una compra y verla reflejada en posiciones, (5) editar una transacción y ver el costo
  promedio recalculado, (6) eliminar una transacción, y (7) verificación de accesibilidad con
  `@axe-core/playwright` en cada vista.

---

## 7. Despliegue

### 7.1 Frontend en Vercel

Build estático (`vite build`), sin runtime de Node en producción. `VITE_API_BASE_URL` configurada por
entorno (preview y producción). Cabeceras de seguridad y reescritura SPA en `vercel.json`. El deploy lo
dispara Jenkins con `vercel deploy --prod --token=$VERCEL_TOKEN` para mantener un solo pipeline.

### 7.2 Backend en el VPS Hostinger

`deployment/Dockerfile` multi-stage: `eclipse-temurin:25-jdk` para compilar, `eclipse-temurin:25-jre`
para ejecutar, usuario no root, `HEALTHCHECK` contra `/actuator/health`, `-XX:MaxRAMPercentage=75`.

`deploy/docker-compose.prod.yml` levanta `postgres:17-alpine` con volumen persistente, el backend, y
Nginx como reverse proxy con TLS de Let's Encrypt, en la misma red que n8n. CORS restringido al dominio
de Vercel. Secretos en `.env` en el VPS (permisos 600) y en el almacén de credenciales de Jenkins;
nunca en Git. Respaldo diario de Postgres con `pg_dump` por cron.

### 7.3 Pipeline de Jenkins

`Jenkinsfile` declarativo, idéntico en local y en el VPS.

```
Checkout
 → Backend: Build & Test        (gradle build jacocoTestReport, gate 80%, validateStructure)
 → Frontend: Build & Test       (npm ci, tsc --noEmit, eslint, vitest --coverage, vite build)
 → Docker Build                 (imagen del backend etiquetada con el SHA del commit)
 → Acceptance Tests             (docker compose up, Cucumber, Playwright, compose down)
 → Publish Reports              (JaCoCo, JUnit, Cucumber HTML, Playwright HTML)
 → Deploy Backend               (según DEPLOY_TARGET)
 → Deploy Frontend              (vercel deploy --prod)
 → Smoke Test                   (health check + un flujo crítico de Playwright contra producción)
```

Parámetro `DEPLOY_TARGET`:

- `local`: `docker compose up -d` en el host de Jenkins. Para desarrollar y validar el pipeline.
- `vps`: contexto Docker remoto por SSH hacia el VPS Hostinger. Despliegue real.
- `none`: solo construir y probar. Valor por defecto en ramas distintas de `main`.

`jenkins/docker-compose.yml` levanta Jenkins LTS con Configuration as Code (`casc/jenkins.yaml`) y
`plugins.txt`, de modo que la misma configuración se reproduce en el Mac y en el VPS. En local se usa
polling de SCM (sin IP pública no llegan webhooks); en el VPS, webhook de GitHub.
El `Jenkinsfile` se valida con el linter declarativo antes de cada commit que lo toque.

---

## 8. Fases de ejecución

Cada fase: TDD (prueba primero), termina en verde, con un commit Conventional Commits en inglés, y un
punto de revisión con el usuario. La ejecución del código se delega a subagentes con Sonnet; el plan y
las revisiones de arquitectura van con Opus.

| # | Fase | Entregable | Verificación |
|---|---|---|---|
| 0 | Verificación de entorno | Informe de viabilidad | `java -version` = 25; `gradle ca --javaVersion=VERSION_25` acepta el valor; Docker y Compose disponibles; RAM y disco libres del VPS; n8n responde |
| 1 | Scaffold y monorepo | `backend/` generado, `.gitignore`, `README.md`, ADR-001…005 | `gradle build` y `gradle validateStructure` en verde |
| 2 | Dominio: value objects y puertos | `domain/model` completo | Pruebas JUnit 5 de value objects; `domain` sin dependencias de framework |
| 3 | Casos de uso de portafolio | Transacciones, posiciones, P&L, retorno total | Pruebas con Mockito, incluido el efecto cambiario en 3 segmentos |
| 4 | Casos de uso de señales | Scoring combinado, diversificación | Pruebas de redistribución proporcional de pesos y de las reglas de asignación |
| 5 | Adaptador JPA | Entidades, mappers, Flyway, semillas | `@DataJpaTest` con Testcontainers; el portafolio actual migrado |
| 6 | Adaptador de n8n | `rest-consumer` con Resilience4j y caché | MockWebServer: éxito, fallo, timeout, circuit breaker |
| 7 | Seguridad | `security-jwt`, Spring Security, login | Pruebas de token: válido, expirado, manipulado |
| 8 | Entry point REST | Controladores, DTO, `ProblemDetail`, OpenAPI | MockMvc por endpoint; 401 sin token |
| 9 | Aceptación del backend | Cucumber + WireMock + Testcontainers | Todos los escenarios en verde; cobertura ≥80% |
| 10 | Design system del frontend | Tokens, primitivas, layout de la shell | Vitest sobre primitivas; contraste AA verificado |
| 11 | Autenticación del frontend | Login, guardia de rutas, cliente de API con Zod | Vitest + MSW: login correcto, credenciales inválidas, expiración |
| 12 | Vistas del dashboard | Las 8 vistas, incluido el CRUD de transacciones | Vitest + MSW por vista; estados de carga, error y vacío |
| 13 | Playwright | Page objects, fixtures, flujos y accesibilidad | Suite en verde en Chromium y WebKit |
| 14 | Contenedores | Dockerfile, `docker-compose.prod.yml`, Nginx | Stack arriba en local; health check en verde; aceptación contra el stack |
| 15 | Jenkins | `Jenkinsfile`, Jenkins con JCasC, credenciales | Pipeline completo en verde en local con `DEPLOY_TARGET=local` |
| 16 | Despliegue | Frontend en Vercel, backend en el VPS | Pipeline en verde con `DEPLOY_TARGET=vps`; smoke test contra producción |

---

## 9. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| El plugin de Bancolombia no acepta `VERSION_25` | Bloquea la fase 1 | Generar con `VERSION_21` y subir el toolchain a 25 en `build.gradle`. Spring Boot 4.0 soporta Java 25 con baseline 17. Se verifica en la fase 0 |
| RAM insuficiente en el VPS (JVM + Postgres + n8n necesitan ≥2 GB) | Bloquea la fase 16 | Medir en la fase 0. Si no alcanza: Postgres al free tier de Neon y solo la JVM en el VPS, con `MaxRAMPercentage` ajustado |
| Cambios de contrato en las respuestas de n8n | Rompe el adaptador en silencio | Fixtures capturados de respuestas reales, deserialización tolerante a campos desconocidos, y validación con Zod también en el frontend |
| La síntesis IA tarda ~55 s | Timeouts en la primera carga | n8n ya sirve desde caché en `staticData`; el backend añade su propia caché y sirve el último snapshot persistido como respaldo |
| Jenkins local no recibe webhooks de GitHub | CI lento en desarrollo | Polling de SCM en local; webhook solo en el VPS |
| Deriva entre el prototipo y el producto nuevo durante la migración | Trabajo duplicado | El prototipo queda congelado como referencia de solo lectura desde la fase 1 |
| Exposición de claves de API en el frontend | Fuga de credenciales | Ninguna clave llega al navegador: todas las llamadas externas salen del backend |

---

## 10. Criterios de aceptación del proyecto

1. `cd backend && gradle build` pasa con cobertura JaCoCo ≥80% y `validateStructure` en verde.
2. `cd frontend && npm test` y `npm run build` pasan; `tsc --noEmit` sin errores.
3. La suite de Cucumber cubre los seis grupos de escenarios de la sección 6.2 y está en verde.
4. La suite de Playwright cubre los siete flujos de la sección 6.3 en Chromium y WebKit, sin
   infracciones de accesibilidad detectadas por axe.
5. El pipeline de Jenkins corre de punta a punta con `DEPLOY_TARGET=local` y con `DEPLOY_TARGET=vps`.
6. El frontend en Vercel consume el backend del VPS y reproduce todo lo que hace el dashboard actual,
   más el CRUD de transacciones, el histórico de señales y el informe de diversificación.
7. El navegador no contiene ninguna URL de n8n ni ninguna clave de API.
8. El portafolio se administra desde la interfaz: cambiarlo no requiere tocar código ni redesplegar.
9. Todo el historial de commits sigue Conventional Commits en inglés.
