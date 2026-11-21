# Informe de validación en ejecución

**Generado:** 2026-11-20

**Objetivo:** `ProAula/aula`

## Resumen

| Nivel | Estado | Código de salida | Resultado |
|---|---|---:|---|
| Inicio de aplicación | PASS | 0 | `node start-test-server.js`; Playwright alcanzó `http://127.0.0.1:3466/solicitar-codigo-admin`. |
| Pruebas Java | PASS | 0 | 201 aprobadas; 0 fallidas, 0 errores y 0 omitidas. |
| Playwright E2E | PASS | 0 | 52 aprobadas; 0 fallidas. |
| OSRM en vivo | PASS | 0 | 1 prueba independiente aprobada; una ruta representativa recibió y dibujó geometría. |

**Resultado general: PASS con límites de entorno y datos documentados abajo.**

## Entorno y niveles de validación

- Docker: **NO DISPONIBLE** — `Get-Command docker -ErrorAction SilentlyContinue` no encontró el ejecutable; por ello no se pudo consultar el daemon con `docker info`.
- Node.js: **DISPONIBLE** — `node --version`: `v26.7.0`.
- Playwright: **DISPONIBLE** — `npx playwright --version`: `1.64.0`. Chromium arrancó correctamente durante las pruebas.
- Infraestructura: **FALLBACK (H2 en memoria)** — las pruebas usan la base embebida de validación; MySQL/TiDB no se verificó en este entorno.
- Navegador: **PRIMARY (Playwright)** — el portal Thymeleaf fue probado con Chromium; no se omitió el nivel de navegador.
- Java/Maven: Java `27`; Maven Wrapper `3.9.11`.

## Ejecuciones

### Integración Java

- Comando: `.\mvnw.cmd test`
- Código de salida: `0`
- Resultado: `201` pruebas; `0` fallidas, `0` errores, `0` omitidas.
- Alcance: controladores, servicios, persistencia H2, autenticación, permisos, incidentes, rutas y llegadas estimadas.

### E2E de navegador

- Comando: `npx playwright test`
- Código de salida: `0`
- Resultado: `52` pruebas aprobadas; `0` fallidas.
- Alcance destacado: registro/inicio de sesión y perfil; consentimiento; privacidad administrativa; catálogo público y privado; planificación de 20 registros elegibles y 40 segmentos consecutivos confirmados; límites catastrales; incidentes; y estimación de llegadas.
- Las consultas OSRM del catálogo usan respuestas simuladas para que la cobertura sea repetible.

### Geometría de ruta con OSRM real

- Comando PowerShell: `$env:BUSTRAKER_E2E_BUSLOCATIONEVIDENCE='true'; $env:BUS_EVIDENCE_DIR='..\src\Avance\Evidencias'; npx playwright test tests/bus-location-evidence.spec.js --reporter=list`
- Código de salida: `0`
- Resultado: `1` prueba aprobada.
- Evidencia: `Evidencias/01-planificacion-geometria-OSRM.png`.
- Límite: corresponde a una ruta de prueba seleccionada del servidor H2; no confirma que el catálogo sea oficial ni valida la precisión de todas las rutas.

## Casos representativos y capturas

El documento [Evidencias de pruebas clave Bustraker.docx](./Evidencias%20de%20pruebas%20clave%20Bustraker.docx) contiene cinco casos, cada uno con tipo, objetivo, resultado, limitaciones y captura:

1. Planificación de ruta y geometría vial.
2. Cuenta, autenticación y controles administrativos.
3. Límites catastrales de barrios y localidades.
4. Envío autenticado de incidentes.
5. Llegadas estimadas y proximidad con datos simulados.

## Límites no cubiertos

- H2 no demuestra compatibilidad definitiva con MySQL/TiDB; Docker no estuvo disponible.
- Los datos semilla de rutas no se contrastaron con una fuente oficial vigente.
- Solo una ruta se revisó con OSRM real; el resto de solicitudes de planificación usó mocks.
- La estimación de llegada usa ubicación y reportes simulados; no prueba GPS físico ni telemetría real.
- La línea que aparece en la captura de llegadas usa geometría sintética del fixture; no es evidencia de un trazado real por calles.
- Google OAuth y otros proveedores no se autenticaron contra servicios externos.
- No se ejecutaron JMeter, OWASP ZAP ni pruebas contra el despliegue en Render.
- La suite pasa en el entorno de validación; la disponibilidad y comportamiento de producción quedan sin verificar.

## Revisión de preparación de TiDB Cloud y Render

- Se consultó `https://proaula.onrender.com/actuator/health` el 20 de noviembre de 2026. La solicitud agotó el límite de 25 segundos (`curl` código 28, sin respuesta HTTP); no se pudo confirmar que Render esté disponible ni que su instancia alcance TiDB.
- El perfil `prod` requiere `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` y `JWT_SECRET`. Usa MySQL Connector/J y `sslMode: VERIFY_IDENTITY`; el puerto se toma de `PORT`, provisto por Render.
- Hay 14 migraciones SQL para MySQL, pero `application-prod.yml` desactiva Flyway y fija `spring.jpa.hibernate.ddl-auto: none`. Por tanto, el arranque no aplica esas migraciones automáticamente: TiDB debe tener el esquema preparado externamente. No se verificó el historial ni el esquema de la instancia TiDB.
- El repositorio incluye un `Dockerfile`, pero no se encontró `render.yaml` ni un workflow de GitHub Actions. La conexión del servicio a este repositorio, el auto-deploy y las variables configuradas en el panel de Render no se pudieron inspeccionar.
- `aula/.env` está ignorado por Git y no está versionado; no se usó ni se incluyó en los cambios.

## Incidencias resueltas durante la revisión

Se corrigieron selectores y expectativas E2E que habían quedado obsoletos tras cambios de interfaz, aserciones Java desactualizadas y desbordamiento horizontal de la navegación móvil. Las suites completas volvieron a pasar después de esos ajustes.
