# Resultados de las pruebas de performance

Ejecución del 2026-11-26 sobre el commit `348e1f8`, imagen `inversiones-backend:348e1f838a62`
(la misma que construyó Jenkins), stack `deploy/docker-compose.dev.yml`.

**Entorno:** MacBook Apple Silicon, 8 núcleos, 16 GB. k6 v2.4.0. Postgres 17 en contenedor.
El generador de carga y el sistema bajo prueba comparten la máquina: los límites medidos son un
**piso**, no el techo de la aplicación.

**Superficie medida:** sólo `GET /api/v1/transactions` y `GET /api/v1/signals` en carga. Son los dos
únicos endpoints cuyo cierre transitivo de dependencias no contiene un gateway hacia n8n, así que son
los únicos cuyos tiempos son atribuibles a este código y no a la red de un tercero. El razonamiento
completo está en `perf/lib/endpoints.js`.

## smoke — 1 iteración, todos los endpoints

`perf/results/smoke.txt`

| Métrica | Valor |
|---|---|
| Checks | 100 % (todos los endpoints responden 200, el no autenticado responde 401) |
| Peticiones fallidas | 0 / 10 |
| Duración p95 | 688 ms |
| Mediana | 91 ms |

Los tiempos altos son los endpoints que atraviesan n8n en frío (`/ai-synthesis`, `/watchlist`,
`/earnings` no tienen caché). El smoke verifica **disponibilidad**, no latencia.

## load — 30 iter/s (60 req/s) durante 1 minuto

`perf/results/load.txt` — **todos los umbrales en verde.**

| Umbral | Resultado |
|---|---|
| `checks` = 100 % | 100.00 % (5 104 checks) |
| `http_req_failed` = 0 % | 0.00 % |
| p95 < 150 ms | **13.04 ms** |
| p99 < 500 ms | **18.32 ms** |
| p95 `/transactions` < 200 ms | **14.42 ms** |
| p95 `/signals` < 200 ms | **10.63 ms** |
| iteraciones estado estable > 1 750 | 1 801 |
| `dropped_iterations` < 10 | 0 |

Estado estable: mediana 4.82 ms, p90 10.71 ms, máximo 61 ms. El calentamiento del JIT se mide en una
fase aparte de 25 s que queda fuera de todos los umbrales — por eso el máximo global (248 ms) es mayor
que el del estado estable.

## stress — rampa de 20 a 900 iter/s en 2 m 30 s

`perf/results/stress.txt`

| Métrica | Valor |
|---|---|
| Pico de carga aplicada | 898 iter/s ≈ **1 796 req/s** |
| Iteraciones completadas | 44 450 (0 descartadas) |
| Peticiones | 88 901 — 0 fallidas |
| Checks | 100.00 % |
| p95 | **4.53 ms** (p90 3.25 ms, máximo 130 ms) |

**No se encontró el punto de quiebre.** La rampa llegó a su tope sin degradar: cero iteraciones
descartadas significa que k6 aplicó toda la carga pedida, y la latencia *bajó* respecto a `load.js`
porque a esa altura el JIT ya había compilado las rutas calientes. Para encontrar el límite real
haría falta subir el tope de la rampa y, sobre todo, un generador de carga en otra máquina.

Nótese que la latencia de `stress` es menor que la de `load`: no es una contradicción, es el efecto
del calentamiento. Comparar los dos números como si midieran lo mismo sería un error de lectura.
