# Inversiones

Dashboard personal de inversiones: seguimiento de portafolio, P&L por posición, señales combinadas
(técnicas + noticias + analistas) y síntesis asistida por IA sobre un portafolio multi-broker y
multi-moneda (COP/USD).

Backend Java 25 / Spring Boot 4 con arquitectura limpia (scaffold Bancolombia), frontend React 19 +
Vite + TypeScript, Postgres 17, y un pipeline de Jenkins que construye, prueba y despliega.

## ⚠️ Advertencia: dependencia de n8n

La ingesta de datos de mercado **no está en este repositorio**. Vive en workflows de n8n en un VPS
(ver `docs/adr/ADR-004-n8n-as-ingestion-layer.md`). Consecuencias al levantar el proyecto:

- La aplicación **arranca y pasa el smoke test sin acceso a n8n**. No lo contacta al iniciar.
- **Funcionan siempre** (sólo base de datos): login, `GET /api/v1/transactions`, `GET /api/v1/signals`,
  y todo el CRUD de transacciones.
- **Requieren n8n alcanzable** en `N8N_BASE_URL`: `/portfolio`, `/portfolio/returns`,
  `/diversification`, `/ai-synthesis`, `/watchlist`, `/earnings`. Sin n8n **degradan** (datos vacíos o
  en caché, con el circuit breaker abierto) en lugar de fallar — pero el dashboard se verá incompleto.
- `POST /api/v1/signals/refresh` tarda ~95 s y consulta n8n para los 20 instrumentos. Tiene un guard
  de ejecución única: una segunda llamada concurrente responde 409 por diseño.
- Los endpoints que pasan por n8n consumen **cuota de APIs de terceros medidas** (Finnhub, FMP, Yahoo
  Finance, MiniMax). Por eso las pruebas de carga nunca los tocan.

`N8N_BASE_URL` por defecto apunta al VPS del autor. Si no tienes acceso, el backend sigue siendo
utilizable con la superficie de base de datos.

## Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Docker + Compose v2 | — | Postgres, imagen del backend, Testcontainers |
| Java 25 (JDK) | 25 | `./gradlew` en el host (no hace falta para `docker compose`) |
| Node | 22 | frontend, Vitest, Playwright |
| k6 | ≥ 1.0 | pruebas de performance (`brew install k6`) |

Usa siempre el wrapper `backend/gradlew`; un Gradle 8.x del sistema no puede correr sobre Java 25.

## Levantar el backend

```bash
cp deploy/.env.template deploy/.env          # los valores por defecto sirven para local
docker compose -f deploy/docker-compose.dev.yml up -d --build --wait
./scripts/smoke.sh                           # health + login + llamada autenticada + rechazo 401
```

El backend queda en `http://localhost:8080`. Flyway corre las migraciones y siembra los datos de
referencia al arrancar. Si el puerto 5432 ya está ocupado, pon `POSTGRES_PORT=5433` en `deploy/.env`.

**Credenciales sembradas (sólo local):** usuario `david`, contraseña `Inversiones2026!`. El hash está
en `V2__seed_reference_data.sql` y la migración `V4` lo rota a partir del placeholder
`SEED_PASSWORD_HASH`, que en el perfil `local` trae el valor comprometido y en producción es
obligatorio. No es un secreto de producción.

Parar: `docker compose -f deploy/docker-compose.dev.yml down` (añade `-v` para borrar la base).

## Levantar el frontend

```bash
cd frontend
npm ci
cp .env.example .env                         # VITE_API_BASE_URL=http://localhost:8080
npm run dev                                  # http://localhost:5173
```

`VITE_API_BASE_URL` es la única entrada de entorno que lee la app. No pongas secretos: Vite inlinea
toda variable `VITE_*` en el bundle del cliente. El backend ya permite el origen `http://localhost:5173`
por CORS.

Build de producción: `npm run build` (hace `tsc --noEmit` antes de compilar).

## Pruebas

### Backend

```bash
cd backend
./gradlew test --no-configuration-cache             # unitarias, sin Docker
./gradlew acceptanceTest --no-configuration-cache   # Cucumber: 6 features contra la API HTTP real,
                                                    # Testcontainers Postgres + WireMock como n8n
./gradlew build --no-configuration-cache            # + JaCoCo mergeado (gate 80 % LINE) + validateStructure
./gradlew validateStructure                         # falla si domain/ toca un framework
```

PIT (mutación) está disponible pero **fuera de `build`**, a propósito: `./gradlew pitest` por módulo o
`./gradlew pitestReportAggregate`. Acoplarlo a `build` costaba 13 minutos y provocaba OOM en el agente.

### Frontend (Vitest)

```bash
cd frontend
npm test                 # unitarias + componentes, MSW para la API
npm run test:coverage
npm run lint
```

### E2E (Playwright) — 41 pruebas en 9 archivos, Chromium + WebKit

Requiere el **backend levantado** (sección anterior): las pruebas corren contra la API real, no mocks.
Playwright arranca el frontend solo (`npm run build && npm run preview` en el puerto 5173).

```bash
cd frontend
npx playwright install --with-deps     # sólo la primera vez
npm run e2e                            # suite completa
npm run e2e:ui                         # modo interactivo
npx playwright test e2e/specs/auth.spec.ts          # un solo archivo
npx playwright test --project=chromium              # un solo navegador
npx playwright show-report                          # informe HTML del último run
```

Qué cubre: autenticación y protección de rutas, ciclo de vida de transacciones (registrar → editar →
eliminar, con recálculo de costo promedio), ordenamiento de señales, refresh manual, degradación
cuando el efecto cambiario no está disponible, accesibilidad con `@axe-core/playwright`, que el token
viva en `sessionStorage` y nunca en `localStorage` (`auth.spec.ts`), y que el bundle de producción no
contenga la credencial sembrada (`bundle-security.spec.ts`).

El proyecto `setup` hace login una vez y guarda el estado en `playwright/.auth`; los demás proyectos
dependen de él. Trazas y capturas sólo se guardan en fallo.

#### Árbol

```
frontend/e2e/
├── auth.setup.ts                       login una vez, guarda storageState
├── global-setup.ts                     verifica que la API responda antes de empezar
├── fixtures/
│   └── authenticated.ts                fixture con sesión ya iniciada
├── pages/                              Page Objects
│   ├── AppShellPage.ts
│   ├── LoginPage.ts
│   ├── PosicionesPage.ts
│   ├── SenalesPage.ts
│   └── TransaccionesPage.ts
├── specs/
│   ├── accessibility.spec.ts
│   ├── auth.spec.ts
│   ├── bundle-security.spec.ts
│   ├── currency-effect-unavailable.spec.ts
│   ├── posiciones.spec.ts
│   ├── senales-sort.spec.ts
│   ├── signals-refresh.spec.ts
│   └── transacciones-lifecycle.spec.ts
└── support/
    ├── authFile.ts
    ├── axe.ts
    └── credentials.ts                  credencial sembrada, deliberadamente pública
```

### Performance (k6)

Requiere el **backend levantado**. Los tres escenarios son distintos en propósito, no en intensidad:

```bash
k6 run perf/scenarios/smoke.js     # 1 iteración, todos los endpoints: ¿responden?   (~2 s)
k6 run perf/scenarios/load.js      # 30 iter/s durante 1 min, umbrales estrictos     (~1 m 25 s)
k6 run perf/scenarios/stress.js    # rampa 20 → 900 iter/s hasta degradar            (~2 m 30 s)

BASE_URL=https://api.ejemplo.com k6 run perf/scenarios/smoke.js   # contra otro entorno
```

- **`load.js`** usa modelo **abierto** (`constant-arrival-rate`): la carga ofrecida no baja cuando el
  sistema se pone lento, que es lo que hace un `vus: 50` y lo que arruina la medición. Un umbral en
  rojo aquí **es un fallo**. El calentamiento del JIT se mide en una fase aparte excluida de los umbrales.
- **`stress.js`** tiene umbrales deliberadamente laxos: sólo asegura corrección (0 % de 5xx, 100 % de
  checks). Un umbral de latencia en rojo aquí **es el resultado**, no un fallo.
- Sólo se cargan `GET /transactions` y `GET /signals`. Son los dos únicos endpoints cuyo cierre
  transitivo de dependencias no contiene un gateway hacia n8n. El razonamiento — y el error que lo
  corrigió, un *cache stampede* que abrió tres circuit breakers a 224 req/s — está documentado en
  `perf/lib/endpoints.js`.

**Resultados medidos (2026-11-26, commit `348e1f8`): [`perf/results/README.md`](perf/results/README.md)**

| Escenario | Carga aplicada | p95 | Fallos | Umbrales |
|---|---|---|---|---|
| smoke | 10 req | 688 ms | 0 | — |
| load | 60 req/s × 1 min | **13.04 ms** | 0 / 5 105 | 8 / 8 ✅ |
| stress | pico 1 796 req/s | **4.53 ms** | 0 / 88 901 | 3 / 3 ✅ |

La rampa de stress llegó a su tope sin encontrar el punto de quiebre. El generador de carga comparte
máquina con el sistema bajo prueba, así que estos números son un piso, no el techo de la aplicación.

#### Árbol

```
perf/
├── lib/
│   ├── auth.js                 login y cabeceras autenticadas; BASE_URL configurable
│   └── endpoints.js            catálogo clasificado: DB_ONLY / UPSTREAM / EXCLUDED
├── scenarios/
│   ├── smoke.js                disponibilidad de todos los endpoints
│   ├── load.js                 estado estable con umbrales estrictos
│   └── stress.js               rampa hasta degradar
└── results/
    ├── README.md               informe de la última ejecución
    ├── smoke.txt               salida cruda de k6
    ├── load.txt
    └── stress.txt
```

## CI/CD

Jenkins corre en contenedor, configurado íntegramente como código (JCasC + job-dsl); nada se
configura con clics.

```bash
cp jenkins/.env.template jenkins/.env        # pon JENKINS_ADMIN_PASSWORD
docker compose -f jenkins/docker-compose.yml --env-file jenkins/.env up -d --build
./jenkins/lint-jenkinsfile.sh                # valida el Jenkinsfile antes de lanzar un build
```

Jenkins queda en `http://127.0.0.1:8090` (ligado a loopback a propósito: monta el socket de Docker).
Clona `master` de GitHub por SSH con una deploy key de sólo lectura y hace polling cada 15 min.

El pipeline: guards de seguridad → build y tests del backend → build y tests del frontend → imagen
Docker etiquetada con el commit → stack de aceptación (Cucumber + Playwright, con una aserción de que
el stack corre *la imagen recién construida*) → smoke test → despliegue (`DEPLOY_TARGET=local|vps`).

Detalles de despliegue en VPS, secretos y rotación: `docs/runbook-vps.md`.

## Estructura

```
inversiones/
├── spec.md                     especificación de planeación
├── Jenkinsfile                 pipeline declarativo
├── backend/                    Gradle multi-módulo, arquitectura limpia, Java 25
│   ├── applications/app-service/      Spring Boot main, DI, properties
│   ├── domain/model/                  entidades, value objects, puertos — cero frameworks
│   ├── domain/usecase/                lógica de negocio — cero frameworks
│   ├── infrastructure/driven-adapters/ JPA/Postgres, consumidor REST (n8n), seguridad/JWT
│   ├── infrastructure/entry-points/   controladores REST
│   └── deployment/                    Dockerfile multi-etapa
├── frontend/                   Vite + React 19 + TypeScript (src/, e2e/)
├── perf/                       escenarios k6 y resultados
├── deploy/                     compose dev y prod, plantilla de entorno, guards
├── jenkins/                    compose, JCasC, plugins, linter del Jenkinsfile
├── postman/                    colección de la API
├── scripts/                    smoke.sh y aserciones del pipeline
└── docs/
    ├── adr/                    ADR-001 .. ADR-005
    └── runbook-vps.md          operación en producción
```

La regla de dependencias la verifica el scaffold: `domain/model` y `domain/usecase` nunca compilan
contra Spring ni JPA. `./gradlew validateStructure` lo comprueba en el pipeline.

## Decisiones de arquitectura

- `docs/adr/ADR-001-postgresql-over-mongodb.md`
- `docs/adr/ADR-002-frontend-vercel-backend-vps.md`
- `docs/adr/ADR-003-imperative-over-reactive.md`
- `docs/adr/ADR-004-n8n-as-ingestion-layer.md`
- `docs/adr/ADR-005-java-25-spring-boot-4.md`
