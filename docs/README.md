# Documentación de ProAula

Este es el índice central de documentación de la aplicación. Los archivos de
compilación y ejecución estándar permanecen en la raíz de `aula/`; los enlaces
de esta tabla apuntan a sus ubicaciones reales.

## Guías e informes

| Documento | Tema | Código, configuración o datos relacionados |
| --- | --- | --- |
| [Mapa de carpetas](./estructura-del-proyecto.md) | Propósito de las carpetas, componentes y archivos de entrada. | Estructura completa de [`ProAula`](../) |
| [Auditoría de uso y rendimiento](./auditoria-inventario-y-rendimiento.md) | Inventario, evidencia de uso, candidatos de limpieza y optimizaciones con sus límites. | [`aula/`](../aula/), [`ModelosRepo/`](../ModelosRepo/), [`data/`](../data/) |
| [Desarrollo local](./local-development.md) | XAMPP, perfiles, OAuth y arranque local. | [`application-local.yml`](../aula/src/main/resources/application-local.yml), [lanzadores locales](../aula/scripts/local/) |
| [Configuración de producción](./production-setup.md) | Render, secretos, TiDB, esquema y semillas locales. | [`application-prod.yml`](../aula/src/main/resources/application-prod.yml), [`Dockerfile`](../aula/Dockerfile), [migraciones MySQL](../aula/src/main/resources/db/migration/mysql/), [`ProductionAdminSchemaBootstrapService.java`](../aula/src/main/java/com/proaula/aula/Service/ProductionAdminSchemaBootstrapService.java) |
| [Aprobación de códigos ADMIN](./admin-code-approval.md) | Flujo de solicitud, aprobación y migración de esquema. | Paquetes [`Controller`](../aula/src/main/java/com/proaula/aula/Controller/), [`Service`](../aula/src/main/java/com/proaula/aula/Service/) y [migraciones MySQL](../aula/src/main/resources/db/migration/mysql/) |
| [Llegadas estimadas](./llegadas-estimadas.md) | Comportamiento y presentación de las estimaciones de llegada. | [servicios Java](../aula/src/main/java/com/proaula/aula/Service/), [`map-weather.js`](../aula/src/main/resources/static/js/map-weather.js), [`viajar.html`](../aula/src/main/resources/templates/Usuario/viajar.html) |
| [Migración de datos a TiDB](./tidb-data-migration.md) | Copia manual, explícita y no destructiva desde MySQL. | [herramientas de base de datos](../aula/scripts/database/), [`application-local.yml`](../aula/src/main/resources/application-local.yml), [log local](./logs/migracion_tidb.log) |
| [Validación de ejecución](./runtime-validation-report.md) | Resultados históricos de pruebas y verificaciones. | [`src/test/java`](../aula/src/test/java/), [pruebas E2E](../aula/e2e/), [prueba del planificador](../aula/scripts/maps/test_map_travel.js) |
| [Pruebas E2E](./e2e-testing.md) | Instalación y ejecución de Playwright. | [`package.json`](../aula/e2e/package.json), [`playwright.config.js`](../aula/e2e/playwright.config.js), [`e2e/tests`](../aula/e2e/tests/) |
| [Informe de remediación legal](./INFORME-REMEDIACION-LEGAL.md) | Controles de consentimiento, privacidad y pendientes de despliegue. | [`LegalConsentController.java`](../aula/src/main/java/com/proaula/aula/Controller/LegalConsentController.java), [plantillas legales](../aula/src/main/resources/templates/), [migraciones MySQL](../aula/src/main/resources/db/migration/mysql/) |
| [Evidencias Jira](./README_JIRA_EVIDENCIAS.md) | Trazabilidad de las tareas del planificador de rutas. | [`map-travel.js`](../aula/src/main/resources/static/js/map-travel.js), [`viajar.html`](../aula/src/main/resources/templates/Usuario/viajar.html), [prueba del planificador](../aula/scripts/maps/test_map_travel.js) |

## Organización de archivos auxiliares

- [`aula/scripts/local/`](../aula/scripts/local/): lanzadores HTTP/HTTPS y
  preparación del OAuth local.
- [`aula/scripts/database/`](../aula/scripts/database/): migrador TiDB, sus
  dependencias y certificado CA; también contiene el SQL manual
  [`FIX_ADMIN_ROLES.sql`](../aula/scripts/database/FIX_ADMIN_ROLES.sql).
- [`aula/scripts/maps/`](../aula/scripts/maps/): generador cartográfico y
  verificación del planificador de mapas.
- [`aula/scripts/maintenance/`](../aula/scripts/maintenance/): herramientas de
  mantenimiento de plantillas y recursos.
- [`aula/e2e/`](../aula/e2e/): configuración, dependencias y pruebas de
  navegador; la guía está en este índice.

La raíz de `aula/` conserva `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `Dockerfile`,
`.dockerignore`, `.gitignore` y `src/` en sus ubicaciones convencionales. No se
movieron paquetes Java ni recursos `static` o `templates`.

## Archivos de datos y registros

- La atribución de los datos cartográficos permanece junto al recurso estático en
  [`aula/src/main/resources/static/data/cartografia/README.txt`](../aula/src/main/resources/static/data/cartografia/README.txt);
  se conserva ahí para no cambiar la ubicación pública del recurso.
- [`logs/`](./logs/) reúne registros locales generados. Está excluido de Git:
  `migracion_tidb.log` contiene intentos históricos de migración y
  `test-output.txt` es una salida de pruebas anterior, no un resultado vigente.
  Los comandos actuales de validación deben ejecutarse de nuevo para conocer el
  estado presente.
- `Universidad/test.txt` contiene únicamente texto de prueba y no pertenece a la
  documentación de ProAula.
