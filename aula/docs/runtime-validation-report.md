# Informe de verificación de runtime

## Regresión de limpieza — 2026-11-06

**Objetivo:** comprobar que la eliminación del `package-lock.json` vacío y de siete hojas CSS sin referencias no afecta las funciones existentes.

### Veredicto de esta ejecución

```yaml
environment:
  docker: UNAVAILABLE — no hay comando docker disponible
  node: AVAILABLE — v26.7.0
  playwright: AVAILABLE — 1.64.0; Chromium verificado con `playwright install chromium`
  infra-tier: FALLBACK(embedded/in-memory) — pruebas Java usan el perfil validation con H2
  browser-tier: PRIMARY(Playwright) — disponible, pero no ejecutado porque falló el intento inicial de build/lanzamiento de Java
startup: FAIL — `debugJavaApplication` devolvió Maven build exit_code 1; `mvnw.cmd test-compile` posterior pasó (exit_code 0), pero no se reintentó el lanzamiento
integration: PASS — exit_code: 0; suite Java 155/155 y flujos de integración 39/39
e2e: UNVERIFIED — exit_code: n/a; Playwright no se ejecutó al no quedar una aplicación Java iniciada
overall: FAIL — pruebas Java, Python y lógica de rutas pasaron, pero el arranque falló y la UI en navegador queda sin validar
```

### Evidencia

| Comprobación | Resultado |
|---|---|
| `mvnw.cmd test` | **PASS**, código 0; 155 pruebas, 0 fallos y 0 errores. |
| `mvnw.cmd -Dtest=*FlowIntegrationTest test` | **PASS**, código 0; 39 pruebas, 0 fallos y 0 errores. |
| `mvnw.cmd test-compile` | **PASS**, código 0. |
| `Modelos: python -m unittest discover -s tests -v` | **PASS**; 9 pruebas. |
| API Python en ejecución: salud, métricas, recomendación de ruta y validación de entrada | **PASS**; respuestas 200/422 esperadas. |
| `node scripts/test_map_travel.js` | **PASS**; todos los escenarios del planificador, incluidos resultados directos y transbordos. |
| `node --check` | **PASS** para 25 archivos JavaScript de frontend y E2E. |
| Referencias locales CSS/JS en plantillas | **PASS**; todos los recursos referenciados existen y ninguno de los siete CSS eliminados tiene referencias en `src`. |
| `git diff --check` | **PASS**. |
| `debugJavaApplication` | **FAIL**, el lanzador reportó Maven build exit 1 sin detalle de compilación. No se repitió el arranque; el build independiente `test-compile` y las pruebas sí pasaron. |
| Playwright E2E | **UNVERIFIED**; se omitió por el fallo de arranque anterior. |

Las pruebas Java y Python usan datos de validación/sintéticos y no sustituyen una prueba de integración con MySQL; Docker no está disponible. La prueba visual de navegador queda pendiente, por lo que este resultado no afirma que el E2E esté confirmado.

**Generado:** 2026-10-30T15:43:00-05:00
**Proyecto:** `ProAula/aula`
**Alcance:** llegadas estimadas, controles de acceso, interfaz de viaje y pruebas existentes relacionadas.

## Veredicto

```yaml
environment:
  docker: UNAVAILABLE — comando docker no instalado/disponible
  node: AVAILABLE — v26.7.0
  playwright: AVAILABLE — 1.64.1; Chromium instalado
  infra-tier: FALLBACK(embedded/in-memory) — perfil validation con H2; Docker no disponible
  browser-tier: PRIMARY(Playwright)
startup: PASS — node e2e/start-test-server.js; /actuator/health devolvió 200; Spring Boot inició en ~28.6 s
integration: FAIL — exit_code: 1; suite completa: 125 pruebas, 0 fallos de aserción, 3 errores en SyntheticDataSeederServiceTest
e2e: PASS — exit_code: 0; 13 pruebas, 12 pasaron, 1 omitida; Playwright Chromium
overall: FAIL — defectos reproducibles de respuesta HTTP y errores de esquema de pruebas aún pendientes
```

## Evidencia ejecutada

| Comprobación | Resultado |
|---|---|
| `mvnw.cmd test` | **FAIL**, código 1. 125 pruebas; 3 errores en `SyntheticDataSeederServiceTest`. |
| Pruebas focalizadas de llegadas y páginas | **PASS**, código 0. 20 pruebas: servicio, controlador, flujo API y vistas. |
| `mvnw.cmd -Dtest=*FlowIntegrationTest test` | **PASS**, código 0. 28 pruebas. |
| `npm test` en `e2e` | **PASS**, código 0. 12 pasaron, 1 omitida. |
| `node scripts/test_map_travel.js` | **PASS**; todos los casos reportados por el script. |
| `node --check` | **PASS** para el planificador, el editor de coordenadas y los tests E2E modificados. |
| `git diff --check` | **PASS**. |
| Arranque / `GET /actuator/health` | **PASS**, la aplicación respondió `{"status":"UP"}`. |
| `GET /api/llegadas/999999` y `/api/llegadas/0` | **FAIL funcional**, ambos responden 500 en vez del 404 de ruta inexistente. |
| `GET /api/llegadas/999999?sentido=INVALID` | **PASS de validación**, responde 400. |

## Hallazgos

| # | Prioridad | Evidencia | Resultado |
|---|---|---|---|
| 1 | Alta | `GET /api/llegadas/{id}` con ID inexistente devuelve 500 `INTERNAL_ERROR`; el log confirma que `ResponseStatusException: 404 NOT_FOUND` llega al manejador genérico. | Las respuestas de negocio de la capa de llegadas pueden convertirse en 500 en vez de conservar sus estados previstos. Debe revisarse el tratamiento global de `ResponseStatusException` y probar 404/409/422/429/503. No se modificó código de producción durante esta verificación. |
| 2 | Media | Las tres pruebas de `SyntheticDataSeederServiceTest` fallan con `Column "BUS_ID" not found` al insertar en `incidente`. | La carga sintética inserta `bus_id`, pero el esquema H2 de validación no expone esa columna. La suite completa no queda verde. |
| 3 | Cobertura pendiente | En el perfil E2E la API reportó 0 rutas reales y 0 tramos confirmados; una prueba se omitió por falta de rutas verificadas. | No se pudo demostrar una ETA con datos operativos reales. El E2E de recorridos usa fixtures provisionales para probar la lógica del navegador; no valida la calidad de coordenadas reales ni tráfico. |
| 4 | Operación pendiente | Los reportes de GPS viven en memoria y las coordenadas reales de paradas aún deben cargarse. | Reinicios eliminan reportes y distintas réplicas no comparten datos. Antes de usar ETA en producción, cargar coordenadas verificadas y valorar almacenamiento compartido si habrá más de una instancia. |

## Ajustes de pruebas durante la verificación

Se actualizaron expectativas obsoletas de Playwright: la página pública antigua para solicitar rol ADMIN ya no existe, el registro general no ofrece selección de rol y el resumen del planificador usa “Recomendada”. Tras el ajuste, la suite E2E terminó correctamente.

## Límites

- Docker no está disponible; la comprobación de runtime usó H2 y no sustituye pruebas contra el motor de base de datos de producción.
- Los recorridos provisionales y el servicio OSRM se simularon en pruebas de navegador; la prueba no depende de la red externa para validar la lógica del planificador.
- No se verificó ubicación GPS física ni exactitud de llegada en calle.

## Verificación complementaria de transbordos

Después de corregir el planificador se ejecutaron estas comprobaciones focalizadas:

| Comprobación | Resultado |
|---|---|
| `node --check` para el planificador y `scripts/test_map_travel.js` | **PASS**, código 0; cubre ruta inactiva, paradas inactivas, sentido VUELTA explícito, barrio repetido, enlaces diferenciados y orden por distancia entre paradas. |
| `mvnw.cmd -Dtest=PublicContactAndRouteFlowIntegrationTest test` | **PASS**, código 0; 4 pruebas, 0 fallos y 0 errores. Verifica el contrato por sentido, el filtrado de inactivos y la compatibilidad con paradas legacy. |
| Playwright: prueba de itinerario con transbordo | **PASS**, código 0; 1 prueba. |
| `npm test` en `e2e` | **PASS**, código 0; 12 pruebas aprobadas y 1 omitida. |
| `git diff --check` | **PASS**, código 0. |

La estimación de caminata entre paradas usa distancia en línea recta y no sustituye una ruta peatonal. Los hallazgos generales de la suite completa y las limitaciones de datos reales descritas arriba no se consideran resueltos por estas pruebas focalizadas.
