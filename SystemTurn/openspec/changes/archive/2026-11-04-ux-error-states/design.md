# Design: Honest UI Error States

## Technical Approach

A1 + B1 from the proposal, with no API or backend change. The advisor view uses `turns === null` to mean "unknown". Every failed dashboard load clears `turns` and `nextTurn`. A pure helper `estadoPanel` maps the state to all visible copy. The client service tags the absent-ticket error with `code: 'TURNO_NO_ENCONTRADO'`, and `ClienteView` clears the ticket only for that code. Specs: `specs/browser-e2e` (written), `specs/daily-queue`, `specs/runtime-operations` (note).

## Architecture Decisions

| Decision | Choice | Rejected (tradeoff) |
|---|---|---|
| Unknown marker | `turns: null \| Turn[]`. Keep `status: 'loading'\|'ready'\|'error'` and `nextTurn: Turn\|null` | A new `fase` state (duplicates `status`); A2 stale flag (more copy and tests, and the held data is known wrong after call-next) |
| Clearing | `aplicarResultado` failure branch: `setTurns(null); setNextTurn(null)` | Clearing in each caller (easy to miss the call-next refresh path) |
| Call button | Unchanged condition `!nextTurn \|\| isCalling \|\| status==='loading'`. Clearing makes it native-disabled in every non-ready state | An extra `fase !== 'listo'` term (redundant) |
| Refresh in flight with data | Rendered as ready (rows, "Conectado al servidor"). The appended "Cargando turnos…" row is dropped. The button stays disabled through `status` | Keeping the row (one more branch, no test needs it) |
| Copy derivation | Keep `src/utils/panelAsesor.js` → `estadoPanel(...)` (user chose optional coverage, ~75 lines with tests) | Inline ternaries (5 untested branches spread through the JSX) |
| `isDemoMode` | Remove the import and **delete the export**. The only consumer is `AsesorView`; this closes half of the docs "Dead code" gap | Keep the export (dead code stays) |
| Badge CSS | Rename `.data-mode.demo` → `.cargando` (same amber). Add `.data-mode.error` with the existing alert palette (`#fff0f0`/`#b34e4e`, dot `#b34e4e`). Ready keeps the base green | New tokens (a redesign, out of scope) |
| Error code | `export const TURNO_NO_ENCONTRADO` in `turnosService.js`, imported by the view and the tests | Bare string literals (a typo fails silently) |
| E2E placement | Append the 4 new tests to `03-respuestas-simuladas.spec.js` (4 → 8). They reuse `solicitarTurno` and `RUTA_DASHBOARD` | A new `04` file (duplicates helpers, ~15 extra lines) |

## Data Flow / Transitions

| Event | turns / nextTurn | status | error |
|---|---|---|---|
| Mount | `null`/`null` | loading | '' |
| Load OK | data | ready | kept |
| Any load fails (mount, Reintentar, post-call refresh) | `null`/`null` | error | message + Reintentar |
| Reintentar / refresh starts | kept | loading | '' |
| Call OK → refresh fails | cleared | error | 503 message |
| Conflict → refresh OK / fails | data / cleared | ready / error | conflict msg / refresh msg + Reintentar |
| Non-refreshable call failure | kept (last good load) | ready | message + Reintentar |

## Interfaces

```js
// estadoPanel({ turns, nextTurn, status, visibles }) →
// { fase, insignia, claseInsignia, textoSiguiente, conteo, mensajeTabla|null }
```

| fase (when) | insignia / clase | textoSiguiente | conteo | mensajeTabla |
|---|---|---|---|---|
| `cargando` (turns null, not error) | "Conectando con el servidor…" / `cargando` | "Cargando el siguiente turno…" | "Cargando…" | "Cargando turnos…" |
| `error` (turns null, status error) | "Servidor no disponible" / `error` | "No se pudo consultar el siguiente turno." | "Registros no disponibles" | "No se pudieron cargar los turnos." |
| `listo` | "Conectado al servidor" / '' | existing two strings by `nextTurn` | "{n} registros" | null if `visibles>0`; "Aún no hay turnos registrados hoy." if `n===0`; else "No hay turnos que coincidan con la búsqueda." |

Turn number stays `nextTurn ? number : '—'`. The badge gets `role="status"`. The advisor view still has one `role="alert"`.

Client: in the `ejecutar` catch, `if (causa?.code === TURNO_NO_ENCONTRADO) { setTurno(null); setConfirmarCancelacion(false) }`. After clearing: the card shows "—", "Sin turno" and "Aún no tienes un turno asignado.". The queue shows "Cuando solicites un turno, verás aquí tu posición y tu estado.". "Solicitar turno" is enabled and "Actualizar estado" is unmounted. A 503 or network error keeps `turno`.

## Testing Strategy (RED first)

Vitest: `consultarTurno` absent → `{message:'No encontramos ese turno.', code:'TURNO_NO_ENCONTRADO'}` (RED). Found → normalized with its position. 503 → `SERVICIO_NO_DISPONIBLE`, not the new code. `panelAsesor.test.js` covers 5 cases: cargando, error, listo with data, empty day, filter with no match (RED: the module is missing).

E2E (titles match the spec). Selectors: `getByRole('status')`, strict `getByRole('alert')`, `{ exact: true }` text, cell text (never row counts in error state).

| Test | Mock | Assertions | Fails before the fix on |
|---|---|---|---|
| MOD advisor 503 recovers | dashboard 503 until `unroute(handler)` | status "Servidor no disponible"; card, count and cell copy; the 3 old strings absent; button disabled. After Reintentar: status "Conectado al servidor", error cell gone, `/\d+ registros/` | role status, badge, copy |
| MOD stale ticket | existing `route.fetch` filter | alert unchanged; card has no "Pendiente" and shows "Aún no tienes un turno asignado."; "Cancelar turno"/"Actualizar estado" count 0. Clicking "Solicitar turno" gives "Ya tienes un turno" | card still Pendiente |
| NEW loading | handler pushes each `route` into an array and never resolves (both StrictMode GETs stay pending); `waitForRequest` first | loading copy, the absent strings, button disabled | badge and count copy |
| NEW empty queue | `route.fetch` → `{...cuerpo, turnos:[], siguienteTurno:null}` | "Conectado al servidor", "No hay turnos pendientes", "0 registros", empty-day cell, alert count 0, disabled | empty-day cell |
| NEW refresh failure | after the real load: PATCH `siguiente` → 200 `{}` (no mutation); dashboard → 503; flag + `waitForRequest` | alert; old code count 0 in the region; disabled; "Servidor no disponible" | stale code, enabled button |
| NEW client 503 guard | real ticket, then dashboard 503 | alert has the 503 `mensaje`; card "Pendiente"; "Actualizar estado" visible | none: a guard. Prove it by temporarily clearing on every error |

State safety: mocked advisor tests do not mutate data. The stale-ticket and guard tests each create one pending ticket, and nothing runs after `03`. "Refresh failure" checks `siguienteTurno` is not null first. Each test takes 1–2 screenshots with `captura`.

## File Changes (diff lines)

| File | Est. |
|---|---|
| `src/services/turnosService.js` (code, constant, drop `isDemoMode`) | 8 |
| `src/services/turnosService.test.js` | 35 |
| `src/utils/panelAsesor.js` + `.test.js` (Create) | 30 + 45 |
| `src/views/AsesorView.jsx` | 25 |
| `src/views/ClienteView.jsx` | 6 |
| `src/asesor.css` (single-line file) | 2 |
| `e2e/03-respuestas-simuladas.spec.js` | 130 |
| specs: browser-e2e (97, done), daily-queue MODIFIED (~25), runtime-operations note (~15) | 137 |
| `docs/README.md`: remove 2 UX gaps; Dead-code row lists only `turnosSimulation.js`; counts Vitest 19 (11 + 3 + 5) / e2e 18; roadmap 5 ✅, `infra-network` next | 8 |
| `docs/app.md`: Frontend rows (AsesorView states, ClienteView clear); Tests counts; Browser E2E 18, 03 = 9 | 12 |

Total ≈ 438 lines raw, or ≈ 590–745 with the 1.35–1.7× history. That is under 800.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary.

## Migration / Rollout

No migration required. Revert the PR to roll back.

## Open Questions

- [ ] Spec "Accessible state semantics" says "in both views the connection badge". The client has no badge, so it should read "the advisor badge" (spec phase).
- [ ] The never-resolved routes in the loading test are assumed to close cleanly with the page. If teardown complains, `route.abort()` each one at the end.
