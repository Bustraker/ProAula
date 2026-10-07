# Runtime Validation Report

**Generated:** 2026-10-07
**Target:** `ProAula/aula`
**Scope:** Route planning, direct-route priority, transfers, map rendering, and page integration

## Summary

| Validation | Status | Exit code | Result |
|---|---:|---:|---|
| JavaScript syntax and whitespace | PASS | 0 | Both map scripts passed `node --check`; `git diff --check` passed. |
| Travel-planner harness | PASS | 0 | `scripts/test_map_travel.js` completed with all 25 assertions passing. |
| Database-independent Maven tests | PASS | 0 | 18 tests passed; 0 failures, errors, or skips. |
| Maven test compilation | PASS | 0 | `-DskipTests test-compile` completed successfully. |
| Travel page integration test | PASS (previous run) | 0 | Previously reported 3 tests passed for public and authenticated page rendering; not rerun in this final pass. |
| Browser map-style control | PASS | n/a | Chose Claro, verified Esri attribution changed, and verified the selection persisted after reload. |
| Browser acceptance flows | PASS (previous run) | 0 on final assertions | Prior isolated-fixture checks covered direct-route priority, transfer instructions, estimated walk connector, missing coordinates, validation, disconnected routes, reverse direction, and mobile layout; these flows were not all replayed in this final pass. |
| External map/routing requests | UNVERIFIED | n/a | Browser requests to external map/routing services timed out or reset in this environment, so live tile rendering and OSRM fallback were not confirmed. |
| Full database-backed Maven suite | NOT RUN | n/a | Not rerun because the default Spring test context starts `DataSeederService` and the local configuration points to MySQL with schema updates enabled. |
| Real-data journey | UNVERIFIED | n/a | The local database previously contained no verified routes; synthetic fixtures were used instead. |

**Overall:** NEEDS SIGN-OFF for production data validation. The planner behavior passed with isolated route fixtures; journeys backed by verified database routes remain unverified.

## Environment

| Capability | Result |
|---|---|
| Docker | UNAVAILABLE — Docker CLI is not installed. |
| Node.js | AVAILABLE — v26.7.0. |
| Playwright | AVAILABLE — `npx playwright --version` returned 1.63.0. |
| Chromium | AVAILABLE — installed with `npx playwright install chromium`. |
| Maven wrapper | AVAILABLE — project wrapper completed the selected test runs. |
| Browser fixture | AVAILABLE — isolated local HTTP fixture served the real planner assets on port 4173; stopped after validation and port 4173 verified free. |
| Full application startup | NOT RUN — configured Spring/MySQL startup can seed data and update the schema; browser checks used the isolated fixture instead. |

## Test Evidence

### Java

- Database-independent command:
  `.\mvnw.cmd "-Dtest=*,!AulaApplicationTests,!ConsultasPageIntegrationTest,!RutaPersistenceIntegrationTest" test`
- Result: **18 passed, 0 failed, 0 errors, 0 skipped; exit code 0**.
- A previous run of `ConsultasPageIntegrationTest` reported **3 passed, 0 failed, 0 errors, 0 skipped** for the public and authenticated travel pages. It was not repeated in this pass to avoid a Spring context that may seed/update the configured MySQL database.

### JavaScript

- `node --check src/main/resources/static/js/map-base.js` — passed.
- `node --check src/main/resources/static/js/map-travel.js` — passed.
- `node scripts/test_map_travel.js` — **all 25 assertions passed**, including direct-route priority, one recommended transfer itinerary when no direct route exists, no invented route data, reset behavior, and unavailable-service cases.
- `git diff --check` — passed.
- `.\mvnw.cmd -DskipTests test-compile` — passed.
- `.\mvnw.cmd "-Dtest=*,!AulaApplicationTests,!ConsultasPageIntegrationTest,!RutaPersistenceIntegrationTest" test` — **18 passed, 0 failed, 0 errors, 0 skipped; exit code 0**.
- `ConsultasPageIntegrationTest` had previously passed 3 tests but was not rerun because the Spring test context may seed/update the configured MySQL database.

### Browser acceptance flows

Browser flows used the real travel HTML, CSS, and JavaScript with an isolated local HTTP fixture. Synthetic routes and stops were held in fixture memory only; no route data was written to MySQL. The fixture supplied deterministic street-route geometry so the checks did not depend on external routing or tile services.

In the final browser check, the style menu exposed Calles, Oscuro, Claro, Satélite, and OSM. Selecting Claro changed map attribution to Esri; after reload, Claro remained selected. External tile and route-service requests failed with connection reset/time-out/abort events in this environment, so this check verifies the style control and saved preference, not visible tile delivery or live route fallback.

The detailed planner acceptance flows below are from the previous fixture validation pass and were not all replayed during this final verification:

| Journey | Result |
|---|---|
| Centro → Crespo, with a direct route and transfer alternatives in the fixture | One direct option shown; transfer alternatives suppressed. |
| Select the direct route | Map line rendered; distance/time display rendered from deterministic fixture geometry. |
| Centro → Torices, with no direct route | Three alternatives shown in ascending order: 1, 2, and 3 transfers. |
| Select the 1-transfer option | Instructions named the alighting stop, next route, and transfer neighborhood; map showed a distinct transfer marker and informative popup; route line rendered. |
| Centro → Crespo, with distinct registered Manga stops | Instructions said to get off at Manga, walk to Manga Norte, and board route M-C. The direct stop-to-stop estimate was 900 m, explicitly not a pedestrian route; the ≥500 m caution appeared. The map rendered two transfer-stop markers and one dashed estimated-walk connector. |
| Centro → Torices, with the Crespo boarding stop missing coordinates | The planner explicitly said the walk could not be confirmed. Only the known Manga connection had a dashed connector; no connector was drawn for the unverified Crespo walk. |
| Centro → Crespo, with the same registered stop name and coordinates at Manga | Instructions said to board M-C at the same stop; no walk or distance estimate was shown. |
| Transfer instructions tab | Each leg identified the route, boarding stop, and alighting stop. It warned that bus lines/times are car estimates and that straight-line stop distances are not pedestrian directions. |
| Faster direct option among multiple direct routes | With the longer route deliberately listed first and a transfer alternative also present, only the direct route passing through fewer intermediate neighborhoods was shown and drawn on the map. |
| Faster transfer itinerary when no direct route exists | With two transfer itineraries available, only the one with fewer transfers and then fewer intermediate neighborhoods was recommended; the competing longer itinerary was not shown. |
| Single recommended result and map | Both fastest-route scenarios rendered exactly one result card and automatically drew the recommended itinerary. |
| Centro → Blas de Lezo, disconnected in the fixture | “No hay combinación de rutas” shown. |
| Empty origin and empty destination | Each field produced its corresponding completion message. |
| Identical origin and destination | Rejected with a prompt to choose a different destination. |
| Unknown origin | Rejected with a prompt to choose a neighborhood from the list. |
| Reverse-only journey | Offered the opposite direction; selecting it produced the direct Centro → Crespo result. |
| 390 px mobile viewport | No horizontal page overflow; planner control remained within the viewport. |
| Browser JavaScript runtime | Previous acceptance flows reported no uncaught application exceptions after correcting one assertion's expected wording. The final session logged external-resource failures and an aborted ArcGIS request during reload; live service behavior remains unverified. |

The fixture was stopped after the browser checks; port 4173 was confirmed free.

## Scope and Gaps

- Fixture routes and stops are synthetic. They demonstrate planner behavior, not the correctness or completeness of Cartagena’s actual route data.
- The browser fixture supplied deterministic geometry in place of OSRM responses. The UI validation distinguishes the street-route line from the dashed transfer connector, but does not certify live browser-to-OSRM connectivity or pedestrian routing. The final browser session also recorded external-service timeouts/resets.
- The full database-backed suite was not rerun. `AulaApplicationTests` and `RutaPersistenceIntegrationTest` start a Spring application context; the project’s default context runs `DataSeederService`, while MySQL is configured with `ddl-auto=update`. Avoiding an additional run prevents unintended local database writes.
- Browser checks were executed interactively against the fixture; no persistent Playwright spec was added to the project. The updated `scripts/test_map_travel.js` harness passed all 25 assertions.
- Actual Cartagena route/stop data, visible live map tiles, and live street-routing fallback remain unverified. Straight-line transfer distances are estimates, not pedestrian directions.
- The recommendation is a route-shape heuristic: direct route first; absent one, fewer transfers, then fewer intermediate neighborhoods. There are no bus schedules or live vehicle positions, so it does not guarantee actual fastest arrival.

## Runtime Verdict

```yaml
environment:
  docker: UNAVAILABLE — Docker CLI missing; Docker-based infrastructure was not used for the isolated fixture.
  node: AVAILABLE — v26.7.0.
  playwright: AVAILABLE — Playwright 1.63.0; browser interactions completed.
  infra-tier: FALLBACK(isolated in-memory fixture) — avoids touching the configured MySQL database; real-data persistence was not exercised.
  browser-tier: PRIMARY(Playwright browser) — map-style control and persistence exercised in Chromium; previous synthetic planner flows are documented above.
startup: PARTIAL — isolated fixture responded at http://127.0.0.1:4173/viajar_public; full Spring app not started because configured startup may seed/update MySQL.
integration: PASS — exit_code: 0; passed: 18, failed: 0, skipped: 0; database-independent Maven tests. Maven test compilation also passed. Previous page integration result: 3 passed, not rerun.
e2e: PARTIAL — style selection and persistence verified; previous planner assertions used synthetic data. Live tile/routing requests timed out or reset; real route data and OSRM fallback remain unverified.
overall: NEEDS_SIGNOFF — planner logic and changes passed available tests; validate against verified route/stop data and functioning map/routing services before relying on live directions.
```
