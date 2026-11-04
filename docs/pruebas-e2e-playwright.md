# Pruebas end-to-end con Playwright

Documentación de la suite de pruebas automatizadas de interfaz del proyecto de inversiones. Explica
qué prueba, cómo está organizada, y **por qué** cada decisión es la que es — con referencias a los
archivos reales para que todo sea verificable.

> Las rutas son relativas a `frontend/`. Todo lo que aquí se afirma fue ejecutado y verificado;
> cuando una afirmación depende de una medición concreta, se indica el número.

---

## 1. Qué es y qué no es una prueba end-to-end

Este proyecto tiene tres niveles de prueba, y conviene no confundirlos porque responden preguntas
distintas:

| Nivel | Herramienta | Cantidad | Qué pregunta responde |
|---|---|---|---|
| Unitaria (backend) | JUnit 5 + Mockito | 289 | ¿Esta clase calcula bien, aislada? |
| Aceptación (backend) | Cucumber JVM | 11 escenarios | ¿La API cumple el contrato, contra una base real? |
| Unitaria (frontend) | Vitest + MSW | 161 | ¿Este componente renderiza bien, con la red simulada? |
| **End-to-end** | **Playwright** | **41** | **¿Funciona el sistema completo, como lo usa una persona?** |

La diferencia esencial: una prueba unitaria de frontend simula la red con MSW y renderiza
componentes en jsdom —un DOM falso, sin motor de layout—. Una prueba end-to-end abre **un navegador
real**, contra **el bundle de producción real**, hablando con **el backend real** y su base de datos.

Eso le permite cazar cosas que ninguna prueba unitaria puede: que CORS esté mal configurado, que el
token no sobreviva una navegación, que un `flex` colapse una columna a 1440 px, que el bundle
minificado rompa algo que el código fuente no rompía. Y cuesta lo que cuesta: la suite tarda 2
minutos, contra 6 segundos de las 161 unitarias. Por eso hay 41 casos end-to-end y no 400 — cada
uno tiene que ganarse su sitio.

---

## 2. Qué prueba la suite

Ocho archivos de especificación, 921 líneas en total contando los auxiliares.

| Archivo | Casos | Flujo |
|---|---|---|
| `e2e/specs/auth.spec.ts` | 3 | Login, logout, y que la credencial nunca llegue pre-llenada |
| `e2e/specs/posiciones.spec.ts` | 1 | El tablero carga y muestra el portafolio sembrado |
| `e2e/specs/senales-sort.spec.ts` | 1 | Ordenar la tabla de señales reordena las filas y el segundo clic revierte |
| `e2e/specs/transacciones-lifecycle.spec.ts` | 3 | Registrar, editar y eliminar una transacción, con el efecto en posiciones |
| `e2e/specs/signals-refresh.spec.ts` | 1 | El refresco manual dispara la actualización y la tabla se repuebla |
| `e2e/specs/currency-effect-unavailable.spec.ts` | 1 | Sin tasa de cambio, la vista lo dice y **no** muestra un cero |
| `e2e/specs/bundle-security.spec.ts` | 1 | El bundle de producción no contiene ningún secreto |
| `e2e/specs/accessibility.spec.ts` | 9 | Cero violaciones de accesibilidad en las nueve rutas |

Eso son 20 casos, que corren en **dos navegadores** (Chromium y WebKit) más una prueba de
autenticación que corre una sola vez: **41 en total**.

### El caso que justifica toda la suite

`currency-effect-unavailable.spec.ts` es el más importante, y vale entender por qué.

El backend tuvo un defecto grave: cuando la fuente de la tasa de cambio (TRM) no respondía,
**fabricaba una tasa** con la fecha de hoy. El efecto cambiario salía exactamente cero, y un cero
en ese campo significa "el tipo de cambio no movió nada" cuando la verdad era "no lo sabemos". Se
corrigió en el backend propagando un `Optional.empty()`.

Pero una corrección en el backend se puede deshacer en el frontend sin que nadie lo note: basta un
`Number(data.exchangeEffect ?? 0)` en cualquier refactor futuro para que el cero vuelva. Ninguna
prueba unitaria del backend lo detectaría —el backend sigue correcto—, y una unitaria del frontend
con un fixture mal escrito tampoco.

Esta prueba es la única que verifica la propiedad de punta a punta. Y mira cómo lo hace
(`e2e/specs/currency-effect-unavailable.spec.ts:73-81`):

```ts
await expect(page.getByTestId('asset-gain-figure')).toHaveCount(0)
await expect(page.getByTestId('exchange-effect-figure')).toHaveCount(0)
await expect(page.getByTestId('total-return-figure')).toHaveCount(0)

const panelText = await posiciones.totalReturnPanel().innerText()
expect(panelText).not.toMatch(/\b0[.,]00\b/)
```

Dos capas. La primera afirma que los tres elementos que mostrarían cifras **no existen en el DOM**.
La segunda es la red de seguridad: ningún `0.00` ni `0,00` en todo el panel, por si un cero se
escapara por un camino que esos `data-testid` no cubren.

Afirmar la **ausencia** de algo es más difícil que afirmar su presencia, y por eso suele omitirse.

---

## 3. Arquitectura de la suite

```
e2e/
├── global-setup.ts              Verifica precondiciones del entorno antes de todo
├── auth.setup.ts                Inicia sesión UNA vez por corrida
├── fixtures/
│   └── authenticated.ts         Un `test` cuyo `page` llega ya autenticado
├── pages/                       Page Objects: un archivo por vista
│   ├── LoginPage.ts
│   ├── AppShellPage.ts
│   ├── PosicionesPage.ts
│   ├── SenalesPage.ts
│   └── TransaccionesPage.ts
├── specs/                       Las pruebas propiamente dichas
└── support/                     Utilidades compartidas
    ├── authFile.ts              Dónde se persiste la sesión
    ├── axe.ts                   El asistente de accesibilidad
    └── credentials.ts           Las credenciales sembradas
```

La regla que organiza esto: **los specs describen comportamiento, nunca contienen selectores.** Si
abres cualquier archivo de `specs/` no encontrarás un `.btn-primary` ni un `#username`. Esos viven
en `pages/`.

---

## 4. Los cinco patrones, uno por uno

### 4.1 Page Objects — separar el "qué" del "cómo"

Sin este patrón, un test se escribe así:

```ts
await page.fill('#username', 'david')
await page.click('.btn-primary')
```

Tres problemas: un cambio de clase CSS rompe todos los tests que la usan, el test no se lee como la
intención de una persona, y los selectores se duplican por toda la suite.

Con el patrón (`e2e/pages/LoginPage.ts`):

```ts
export class LoginPage {
  constructor(private readonly page: Page) {}

  usernameInput(): Locator { return this.page.getByLabel('Usuario') }
  submitButton(): Locator { return this.page.getByRole('button', { name: /iniciar sesión/i }) }

  async login(username: string, password: string): Promise<void> {
    await this.usernameInput().fill(username)
    await this.passwordInput().fill(password)
    await this.submitButton().click()
  }
}
```

Y el test queda: `await loginPage.login(SEEDED_USERNAME, SEEDED_PASSWORD)`.

**Dos detalles importantes:**

**Los métodos devuelven `Locator`, no elementos.** Un `Locator` es *perezoso*: no busca nada al
crearse, describe cómo encontrar algo. Se resuelve cuando actúas sobre él, y **se re-resuelve en
cada reintento**. Por eso `expect(locator).toBeVisible()` espera automáticamente hasta que aparezca.
Ese modelo elimina la mayor parte de los `sleep` que hacían insoportables las suites de Selenium.

**Los selectores son semánticos, no CSS.** `getByLabel('Usuario')` y `getByRole('button', {...})`
consultan el **árbol de accesibilidad**, igual que un lector de pantalla. Esto tiene una
consecuencia valiosa: **si el test encuentra el elemento, es evidencia de que es accesible.** Un
botón sin nombre accesible rompe el test, así que la suite empuja hacia una interfaz usable sin
pedirlo explícitamente. Y un refactor de CSS no rompe nada.

### 4.2 Autenticar una vez — el patrón que hace la diferencia en velocidad

Si cada test iniciara sesión por la UI, 41 casos serían 41 logins: más tiempo, y 41 oportunidades
de fallo intermitente ajeno a lo que el test quería probar.

La solución estándar de Playwright es `storageState()`: inicias sesión una vez, Playwright serializa
cookies y `localStorage` a un archivo, y los demás tests arrancan con ese estado.

**Y aquí esta aplicación no puede usar la solución estándar.** Guarda el token en
`sessionStorage`, no en `localStorage` ni en cookies — una decisión de seguridad deliberada para que
el token no sobreviva al cierre de la pestaña. `storageState()` **no captura `sessionStorage`**, así
que habría capturado nada útil **en silencio**: la suite habría parecido funcionar mientras cada
test, en realidad, corría sin sesión.

Por eso hay una implementación propia en dos piezas.

**`e2e/auth.setup.ts`** inicia sesión por la UI real, lee la entrada de `sessionStorage` a mano, y
la escribe a disco. Nota la verificación de la línea 28:

```ts
const session = await page.evaluate((key) => window.sessionStorage.getItem(key), AUTH_STORAGE_KEY)
if (session === null) {
  throw new Error('Login succeeded but sessionStorage carries no auth entry — cannot seed other specs.')
}
```

Si el login funciona pero no hay token, falla con un mensaje explícito en vez de continuar y dejar
que los 40 tests siguientes fallen por una causa no evidente.

**`e2e/fixtures/authenticated.ts`** la reinyecta en cada página nueva:

```ts
export const test = base.extend({
  page: async ({ page }, use) => {
    const session = fs.readFileSync(AUTH_FILE, 'utf-8')
    await page.addInitScript(({ storageKey, value }) => {
      window.sessionStorage.setItem(storageKey, value)
    }, { storageKey: AUTH_STORAGE_KEY, value: session })
    await use(page)
  },
})
```

**`addInitScript` es la pieza crítica.** Ese script corre en la página **antes que cualquier script
de la aplicación**, en cada navegación. Así, cuando `AuthProvider` hace su primer render y busca el
token, ya está. Si se sembrara después de cargar la página, React ya habría decidido que no hay
sesión y habría redirigido a `/login`.

### 4.3 Fixtures — inyección de dependencias aplicada a pruebas

Es el concepto central de Playwright y el que más cuesta al principio.

Cuando escribes `test('algo', async ({ page }) => ...)`, ese `{ page }` **no es un parámetro que tú
pasas**: es una dependencia que Playwright construye y te inyecta. Eso es un *fixture*. Playwright
trae varios de fábrica: `page`, `browser`, `context`, `request`.

`base.extend({ ... })` permite **reemplazar o añadir** fixtures. En el código de arriba se está
envolviendo el `page` original: lo recibe, le añade un script de inicialización, y llama a
`use(page)` para entregárselo al test. Todo lo anterior a `use()` es preparación; lo posterior
sería limpieza.

Luego cada spec importa `test` de ese archivo en vez de de `@playwright/test`, y recibe páginas ya
autenticadas sin pensar en ello. El spec declara **qué** necesita, no **cómo** se consigue.

**Un matiz que muestra cuidado:** `e2e/specs/accessibility.spec.ts` importa **los dos**:

```ts
import { test as base, expect } from '@playwright/test'
import { test as authTest } from '../fixtures/authenticated'
```

La ruta `/login` usa `base` porque es pública — probarla con sesión sembrada examinaría una página
que ningún visitante real ve. Las siete rutas autenticadas usan `authTest`.

### 4.4 Tests parametrizados — un bucle que genera nueve casos

`e2e/specs/accessibility.spec.ts:35-41`:

```ts
for (const route of AUTHENTICATED_ROUTES) {
  authTest(`${route.path} has zero accessibility violations`, async ({ page }) => {
    await page.goto(route.path)
    await expect(page.getByRole('heading', { name: route.heading, level: 1 })).toBeVisible()
    await expectNoAccessibilityViolations(page)
  })
}
```

Playwright importa el archivo, ejecuta el `for`, y cada iteración **registra** un test con su propio
nombre. Los tests se generan en tiempo de colección, no de ejecución. Por eso en el reporte aparecen
como casos independientes (`/posiciones has zero accessibility violations`, `/senales has...`) y si
uno falla sabes exactamente cuál sin leer un stack trace.

**El `expect` de la línea 38 no es decorativo.** Espera a que el encabezado de la vista esté
visible *antes* de pasar axe. Sin eso, axe analizaría el esqueleto de carga en vez del contenido
real, y reportaría cero violaciones por no haber todavía nada que violarlas.

### 4.5 Interceptación de red — provocar fallos que no puedes provocar de verdad

`e2e/specs/currency-effect-unavailable.spec.ts:57-59`:

```ts
await page.route('**/api/v1/portfolio/returns', async (route) => {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(UNAVAILABLE_RETURNS_RESPONSE) })
})
```

`page.route` intercepta peticiones que coincidan con el patrón y responde lo que tú decidas, sin
tocar el servidor.

Aquí se usa para reproducir una condición que de otro modo exigiría **romper el backend a
propósito**: apuntarlo a un n8n inalcanzable, reiniciarlo, y acordarse de restaurarlo. El comentario
del archivo (líneas 11-23) documenta esa decisión y su motivo: el backend es compartido, romperlo
habría sido un efecto secundario de alcance global para una sola prueba.

**El riesgo de este patrón, y cómo se mitiga.** Interceptar significa que tu respuesta inventada
puede no corresponder a lo que el servidor realmente envía — y entonces pruebas una ficción. Aquí
se mitiga de dos formas: el objeto `UNAVAILABLE_RETURNS_RESPONSE` se construyó contra
`TotalReturnResponseSchema` (el schema de Zod que valida las respuestas reales), y el comentario
deja constancia de contra qué se verificó. **Un fixture de interceptación que nadie coteja con el
contrato real es una de las formas más fáciles de tener una suite verde que no prueba nada.**

---

## 5. La configuración: `playwright.config.ts`

### Projects — el mismo test en varios navegadores

```ts
projects: [
  { name: 'setup',    testMatch: /auth\.setup\.ts/ },
  { name: 'chromium', use: { ...devices['Desktop Chrome'] }, dependencies: ['setup'] },
  { name: 'webkit',   use: { ...devices['Desktop Safari'] },  dependencies: ['setup'] },
]
```

Un *project* es una configuración con la que se corre el conjunto. El project `setup` no es un
navegador: es el test de autenticación, y los otros dos declaran `dependencies: ['setup']`, lo que
garantiza que corra antes.

`devices['Desktop Safari']` usa **WebKit, el motor real de Safari**, no una emulación. Importa
porque WebKit difiere de Chromium en manejo de fechas, `Intl` y tiempos de evento. En esta misma
suite se cazó un fallo que solo ocurría en WebKit: una comprobación de UI perdía una carrera contra
el ciclo de rechazo del 409 bajo carga paralela, y se corrigió observando la respuesta HTTP real en
vez de el estado de un botón.

### Paralelismo

```ts
fullyParallel: true
```

Playwright reparte los tests entre varios procesos (*workers*) — en esta máquina, cuatro. Por eso
en la salida los números no salen en orden: `1, 5, 2, 4, 3, 6, 8…` terminan según quién acabe
primero.

Esto solo es seguro porque **cada test parte de un `page` nuevo y no comparte estado**. Un test que
dependiera de que otro corriera antes fallaría de forma intermitente e inexplicable. Es la razón por
la que `transacciones-lifecycle.spec.ts` agrupa sus tres casos en un `describe` secuencial: ahí sí
hay dependencia de orden, y está declarada.

### Diagnóstico solo cuando falla

```ts
trace: 'retain-on-failure',
screenshot: 'only-on-failure',
```

Un *trace* es una grabación completa: cada acción, el DOM en cada instante, las peticiones de red,
la consola. Es la herramienta de diagnóstico más potente de Playwright, y pesa. Configurado así, una
corrida verde deja el directorio de reportes casi vacío, y una fallida deja exactamente lo que
necesitas para entender el fallo sin reproducirlo.

### Lo que cambia entre local y CI

```ts
forbidOnly: !!process.env.CI,
retries: process.env.CI ? 1 : 0,
reuseExistingServer: !process.env.CI,
```

**`forbidOnly`.** Mientras depuras es normal escribir `test.only(...)` para correr un solo caso. Si
eso se commitea, CI correría **ese test y nada más**, y pasaría en verde habiendo probado una
fracción. Con `forbidOnly`, CI falla el build si encuentra un `.only`. Es una red contra un error
humano casi inevitable.

**`retries: 1` solo en CI.** Esto es más discutible de lo que parece. Reintentar **oculta
inestabilidad**: un test que pasa al segundo intento es un test que falla a veces, y eso suele
delatar una condición de carrera real. La justificación de reintentar en CI es que los agentes
comparten CPU y la contención produce fallos ajenos al código. La de **no** reintentar en local es
que ahí sí quieres verla. Playwright marca como `flaky` los que pasaron al reintento, y en un equipo
serio esa lista se trata como deuda, no como ruido.

**`reuseExistingServer`.** En CI siempre construye desde cero. En local reutiliza lo que ya esté en
el puerto, para que iterar sea rápido — con una trampa que se explica en la sección siguiente.

### La frontera que Playwright no cruza

```ts
webServer: {
  command: `npm run build && npm run preview -- --port 5173 --strictPort`,
  url: BASE_URL,
  ...
}
```

Playwright levanta **el frontend y nada más**. El backend y Postgres deben estar corriendo de antes
(`docker compose -f deploy/docker-compose.dev.yml up -d`).

Podría haberse configurado para levantar todo, y muchos proyectos lo hacen. No se hizo porque un
`webServer` que además arranca una base de datos, corre migraciones y levanta una JVM tarda minutos
y falla por motivos que no son del frontend — y cuando eso pasa, Playwright reporta *"timeout
esperando el servidor"* sin decirte si el problema fue Flyway, el puerto o tu código. Separar las
responsabilidades hace que un fallo de infraestructura se vea como lo que es.

**Dos detalles del comando:**

`npm run build && npm run preview` sirve el **bundle de producción**, nunca el dev server. Es el
artefacto que realmente se despliega: minificado, con *tree-shaking*, con los `import.meta.env`
reemplazados.

`--port 5173 --strictPort`. El 5173 es el puerto del dev server, no el 4173 que `vite preview` usa
por defecto. Se toma prestado porque la lista de orígenes permitidos de CORS en el backend
(`SECURITY_ALLOWED_ORIGINS`) apunta al 5173, y cambiarla exigiría reiniciar el backend solo para
elegir otro puerto. El `--strictPort` evita que Vite busque el siguiente puerto libre si el 5173
está ocupado: sin él, la suite correría contra un puerto que CORS rechaza, y fallaría con errores de
red en vez de con "el puerto estaba ocupado".

---

## 6. Las precondiciones del entorno: `e2e/global-setup.ts`

`globalSetup` corre una sola vez, antes de todo. Aquí verifica dos cosas, y **ambas existen porque
su forma de fallar es una suite que parece haber funcionado**.

### La trampa del dev server

`reuseExistingServer` vale `true` fuera de CI. Si dejas `npm run dev` corriendo, Playwright
**adopta el dev server** en lugar de construir el bundle. Los 41 tests pasan — contra el artefacto
equivocado. Y `vite dev` sirve código fuente sin empaquetar, así que un defecto que solo aparece
minificado (un identificador transformado, un `import.meta.env` reemplazado distinto) es invisible.

La verificación detecta ese caso y aborta con un mensaje que incluye el comando para arreglarlo.

**Cómo se eligió el discriminador, porque la lección importa más que el código.** La hipótesis
inicial fue que `/@vite/client` —el módulo de recarga en caliente— daría 404 en `vite preview`. Se
probó contra ambos servidores y resultó **falsa**: da **200 en los dos**, porque `vite preview`
aplica el fallback de SPA y cualquier ruta desconocida devuelve `index.html`.

Si se hubiera confiado en la intuición, el guardia habría comparado códigos de estado, nunca habría
disparado, y habría sido **peor que no tenerlo** — porque se habría confiado en él.

El discriminador que quedó es lo que el documento raíz realmente referencia, verificado en ambos:

| | dev server | `vite preview` |
|---|---|---|
| scripts del HTML raíz | `/@vite/client` y **`/src/main.tsx`** | **`/assets/index-<hash>.js`** |
| `content-type` de `/@vite/client` | `text/javascript` | `text/html` |

`src="/src/main.tsx"` significa literalmente "estoy sirviendo código fuente sin empaquetar". No es
un truco: es la diferencia real entre los dos modos.

### El backend ausente

Playwright no levanta el backend, así que olvidar `docker compose up` produce decenas de fallos con
errores de red, ninguno de los cuales nombra la causa. La verificación lo convierte en un mensaje.

### Fail fast con mensajes accionables

Hay una jerarquía en cómo falla un sistema, y la mayoría de los proyectos se queda en el primer
escalón:

1. **Falla confusa.** 41 tests fallan con `ERR_CONNECTION_REFUSED` enterrado en un stack trace.
2. **Falla rápida.** Una verificación aborta, pero dice solo "precondición no cumplida".
3. **Falla rápida y accionable.** Dice qué está mal, por qué importa, el comando exacto para
   arreglarlo, y **la evidencia que lo detectó**.

Lo último es lo que casi nadie escribe, y lo más valioso:

```
Evidence: the document at http://localhost:5173/ references "/@vite/client" and "/src/...",
which only the dev server serves.
```

Sin esa línea, si el guardia dispara por error no tendrías forma de saber si se equivocó él o tú.
Con ella, verificas su razonamiento en diez segundos.

### Las tres pruebas que todo guardia necesita

Un guardia que nunca se ha visto fallar es un guardia sin probar. Estos se verificaron así:

| Escenario | Resultado observado |
|---|---|
| Dev server en el 5173 | Falla nombrando el bundle equivocado y cómo detenerlo |
| Backend detenido | Falla nombrando `docker compose up` y que nada escucha |
| Todo correcto | 41/41 pasan; el guardia no dice nada |

---

## 7. Cómo ejecutar la suite

### Precondiciones

```bash
# 1. El backend y la base de datos, corriendo
docker compose -f deploy/docker-compose.dev.yml up -d

# 2. El dev server, DETENIDO (si lo tenías abierto)
lsof -nP -iTCP:5173 -sTCP:LISTEN     # ver qué lo ocupa
```

Si olvidas cualquiera de las dos, `global-setup.ts` te lo dirá con el comando exacto.

### Comandos

```bash
cd frontend

npm run e2e          # la suite completa, reporte HTML
npm run e2e:ui       # modo interactivo (lo mejor para demostrar)
```

Y las variantes útiles:

```bash
npx playwright test --reporter=list            # una línea por test, con su duración
npx playwright test --reporter=line            # una sola línea de progreso: [31/41]
npx playwright test --project=chromium         # un solo navegador, la mitad del tiempo
npx playwright test e2e/specs/auth.spec.ts     # un solo archivo
npx playwright test -g "logout"                # los tests cuyo nombre coincida
npx playwright test --headed                   # con el navegador visible
npx playwright test --debug                    # paso a paso, con el inspector
npx playwright show-report                     # abre el reporte de la última corrida
npx playwright show-trace <ruta-al-trace>      # abre un trace de un fallo
```

**Sobre los reporters:** `line` escribe una sola línea de progreso y produce logs cortos, ideal para
CI. `list` imprime una línea por test con su duración, mejor para desarrollo. `html` genera el
reporte navegable. Se eligen por corrida sin tocar la configuración.

### El modo UI, para demostrar

`npm run e2e:ui` abre una interfaz con el árbol de tests, la posibilidad de correr uno solo, y —lo
más vistoso— un ***time-travel debugger***: cada acción queda grabada con la captura del DOM en ese
instante, y puedes moverte paso por paso hacia adelante y atrás. Para una presentación, se explica
solo.

### La primera vez en una máquina nueva

```bash
cd frontend
npm ci
npx playwright install --with-deps chromium webkit
```

Playwright descarga sus propios binarios de navegador, versionados junto a la librería. No usa el
Chrome que tengas instalado, y eso es deliberado: así la suite da el mismo resultado en tu máquina y
en CI.

---

## 8. Resultados de referencia

Última corrida verificada:

```
Running 41 tests using 4 workers
  41 passed (2.1m)
```

Observaciones de esa salida que vale leer:

- **El `[setup]` corre primero y una sola vez** (2.2 s). Ahí está el patrón de autenticación
  funcionando: los otros 40 arrancan ya autenticados.
- **Un test tarda 1.6 minutos** mientras los demás tardan entre 1 y 5 segundos: el de refresco
  manual de señales, porque dispara una llamada real a n8n para los 20 instrumentos. Que el total
  sean 2.1 minutos y no la suma de todo demuestra que el paralelismo funcionó: los otros 40 corrieron
  mientras ese esperaba.
- **Los nombres de los tests son la documentación.** `a wrong password and an unknown username both
  render the same alert, never a field-specific hint` describe un requisito de seguridad verificable.
  La lista de tests **es** la lista de requisitos comprobados.

---

## 9. Qué no cubre, y por qué

Honestidad sobre los límites:

- **No prueba el despliegue.** Corre contra `localhost`. El pipeline de Jenkins (plan 4) añade una
  prueba de humo contra el entorno desplegado.
- **No prueba responsividad en móvil.** Solo escritorio. Habría que añadir projects con
  `devices['iPhone 15']` y similares.
- **No prueba rendimiento.** Ni tiempos de carga ni métricas Core Web Vitals.
- **La interceptación de red prueba una respuesta que el backend podría dejar de enviar.** El objeto
  de `currency-effect-unavailable.spec.ts` se verificó contra el schema, pero si el contrato cambia y
  nadie actualiza el fixture, la prueba seguiría verde probando una forma obsoleta. Es el riesgo
  inherente al patrón.
- **Dos de las seis fuentes de señales no devuelven datos** del n8n actual, así que las vistas que
  dependen de ellas se prueban en su estado parcial. Eso es estado del entorno, no de la suite.

---

## 10. Referencias

- Documentación oficial: https://playwright.dev
- Mejores prácticas: https://playwright.dev/docs/best-practices
- Locators semánticos: https://playwright.dev/docs/locators
- Fixtures: https://playwright.dev/docs/test-fixtures
- `@axe-core/playwright`: https://github.com/dequelabs/axe-core-npm/tree/develop/packages/playwright

Versiones en uso: `@playwright/test` 1.63, `@axe-core/playwright` 4.13, Vite 8.3, TypeScript 6.0.
