# Auditoría de inventario, uso y rendimiento

## Conclusión ejecutiva

La aplicación principal es `aula/` (Spring Boot); `ModelosRepo/` contiene un
servicio FastAPI separado que sí participa en los flujos predictivos. No se
encontró fundamento para borrar las migraciones, el modelo serializado, los datos
de entrenamiento ni dependencias únicamente porque una búsqueda textual no
mostrara usos.

Se retiró de [`aula/pom.xml`](../aula/pom.xml) una regla que intentaba copiar un
XML inexistente y que Maven ya reportaba como cero recursos copiados. No se
eliminaron los demás candidatos: varios requieren confirmar consumo externo o
respaldar configuración antes de tomar una decisión.

La limitación funcional más relevante no es de velocidad: las predicciones se
entrenan/evalúan con datos sintéticos y la configuración productiva no declara
la URL del servicio de modelos. Sin datos reales etiquetados y un despliegue
conectado del servicio, no se puede afirmar exactitud operativa ni garantizar
que cada predicción se calcule correctamente en producción.

## Alcance y método

Se revisaron la estructura del repositorio, configuraciones de Maven/Spring,
Docker, rutas de integración predictiva, suite de navegador, fuentes y artefactos
de modelos, documentación operativa y referencias de recursos estáticos. Los
conteos se obtuvieron recorriendo archivos y contando líneas automáticamente;
se leyeron con mayor profundidad los flujos donde una omisión puede impedir
arranque, predicción, persistencia o presentación.

Esto no equivale a una lectura manual, carácter por carácter, de cada línea de
los miles de líneas de vistas y CSS. Una búsqueda de referencias tampoco prueba
que un recurso sea inútil: rutas dinámicas, URLs compartidas fuera del código o
contenido de usuarios pueden consumirlo. Por ello, los elementos sin referencia
directa se clasifican como candidatos, no como eliminaciones confirmadas.

## Inventario por áreas

| Área | Contenido y evidencia | Clasificación |
| --- | --- | --- |
| [`aula/`](../aula/) | Aplicación Spring Boot. Inventario: 162 fuentes Java de producción, 42 fuentes Java de prueba, 43 plantillas, 40 hojas CSS, 27 archivos JavaScript y 14 specs Playwright. | Activa; conservar la estructura Maven/Spring `src/main` y `src/test`. |
| [`ModelosRepo/`](../ModelosRepo/) | FastAPI, biblioteca Python, artefacto `delay_risk.joblib`, herramientas y cuatro CSV sintéticos (~7,23 MiB). La API importa los modelos y usa el artefacto. | Activa; mantener servicio, artefacto y datos hasta validar una reconstrucción reproducible. |
| [`data/`](../data/) | Archivos fuente del mapa de barrios usados para producir los recursos cartográficos. | Fuente de datos; conservar junto al generador. |
| [`docs/`](./) | Guías y reportes del proyecto. `docs/logs/` contiene salidas locales excluidas de Git. | Documentación activa; los logs son registros locales, no entradas de ejecución. |
| [`.vscode/`](../.vscode/) | Configuración de lanzamiento/tarea apuntando al módulo `aula/`. | Auxiliar de desarrollo; no es código de runtime. |
| [`.github/`](../.github/) | Hay metadatos de modernización, pero no se encontró un flujo CI bajo `workflows/`. | Metadatos; pendiente automatizar build y pruebas en CI. |
| [`ProAula_merged/`](../ProAula_merged/) | Copia heredada con tres archivos rastreados: Dockerfile, `application.properties` y `package-lock.json`; no tiene `pom.xml` ni aparece como ruta de build activa. | Legado; candidato a retirar después de respaldo y saneamiento de configuración. |
| `aula/src/main/resources/graphql-client/` | Directorio vacío, sin código/configuración/dependencias GraphQL detectados. | Inactivo; no aporta comportamiento actualmente. |

El XML `Barrios_Oficiales_Distrito_de_Cartagena_de_Indias_20261107.xml` no existe
bajo `aula/src/`. La regla Maven asociada copiaba cero recursos; se retiró y se
validó con la suite Maven sin regresiones.

## Flujos predictivos revisados

### Riesgo de retraso

- La interfaz de [`map-travel.js`](../aula/src/main/resources/static/js/map-travel.js)
  envía ruta, día de semana, hora, duración planificada aproximada e índice de
  demanda. La demanda se estima con reglas de hora pico/día laboral; no se
  consulta allí una fuente de demanda observada.
- [`delay_risk.py`](../ModelosRepo/proaula_models/delay_risk.py) entrena un
  clasificador con esas cinco variables. El origen declarado por el modelo es
  `SINTETICOS_DEMOSTRACION`; no es un modelo entrenado con retrasos reales
  históricos.
- El conjunto de variables no incluye directamente clima, tráfico medido,
  posición GPS del bus, incidentes reales ni retraso observado. Esto limita lo
  que se puede inferir; no debe presentarse la probabilidad como exactitud
  garantizada para una operación real.
- El reporte previo registró en un holdout sintético: accuracy 80,82 %,
  balanced accuracy 78,66 %, precision 58,00 %, recall 74,48 %, F1 65,21 %,
  ROC-AUC 87,61 % y Brier 0,1148. Son resultados históricos de datos sintéticos,
  no una medición de rendimiento real. Elevar precision puede bajar recall y
  viceversa; el umbral debe elegirse según el costo de falsos positivos y
  negativos, no intentando maximizar todas las métricas a la vez.

### Recomendación de rutas y destinos

- [`recommendations.py`](../ModelosRepo/proaula_models/recommendations.py)
  implementa reglas/ordenamientos, no un clasificador entrenado. La ruta combina
  ETA, frecuencia de salidas y demora climática, y conserva la ruta habitual si
  el ahorro calculado no supera cinco minutos.
- `congestion_pct` forma parte del contrato de entrada/salida, pero no se suma al
  costo: el código espera que el ETA ya incorpore el tráfico para no contarlo dos
  veces. Hay que confirmar que el productor de `eta_min` efectivamente incluye
  congestión; si no, ese factor se ignora en la decisión.
- Las sugerencias de destino usan catálogo e historial de visitas proporcionado
  por la aplicación y ordenan por frecuencia/recencia; no son predicciones
  aprendidas a partir de datos etiquetados.

### Interfaz y límites de las pruebas

- La spec
  [`model-risk-display.spec.js`](../aula/e2e/tests/model-risk-display.spec.js)
  intercepta la respuesta del modelo y verifica que la interfaz represente
  nivel, porcentaje y accesibilidad correctamente. Esto comprueba la
  presentación ante una respuesta conocida, no la disponibilidad real del
  servicio remoto.
- La segunda prueba permite tanto HTTP 200 como 503. Por eso no exige que el
  servicio esté conectado para que el E2E pase.
- La búsqueda de consumidores de `/api/modelos/rutas/recomendar` y
  `/api/modelos/destinos/sugerir` encontró los endpoints Java y pruebas del
  cliente de servicio, pero no llamadas desde JavaScript o plantillas. Hoy son
  capacidades de backend, no resultados que se haya demostrado que aparezcan
  en una interfaz.
- La validación histórica ejecutó un smoke test del API Python por separado,
  pero no demuestra por sí sola el camino productivo completo Java → FastAPI →
  navegador.

## Riesgos de ejecución y reproducibilidad

1. **URL del servicio predictivo.** [`application.yml`](../aula/src/main/resources/application.yml)
   usa `MODELS_API_BASE_URL` con valor por defecto `http://localhost:8000`.
   [`aula/Dockerfile`](../aula/Dockerfile) empaqueta e inicia solo Java; en
   `ModelosRepo/` no se encontró Dockerfile ni manifiesto de despliegue. Si el
   servicio no se publica por separado y no se configura esa variable, las
   peticiones del modelo fallan como servicio no disponible (503). Se añadió
   este requisito a [Configuración de producción](./production-setup.md).
2. **Health parcial.** El endpoint `/health` de FastAPI devuelve estado sin
   cargar explícitamente el artefacto. Un archivo ausente/corrupto puede no
   manifestarse hasta la primera predicción; conviene añadir readiness que
   cargue/verifique el modelo y probar el artefacto al construir/desplegar.
3. **Pruebas Python fuera del repositorio.** Las 15 pruebas Python indicadas en
   el reporte anterior se ejecutaron en una carpeta hermana `Modelos`, no en
   `ModelosRepo/`. Este último solo contiene bytecode bajo `tests/`, no fuentes
   ejecutables versionadas. El resultado previo no se reproduce desde un clon
   limpio; se corrigió la nota en el
   [informe de runtime](./runtime-validation-report.md).
4. **CI ausente.** No se encontró pipeline de CI en `.github/workflows/` que
   ejecute Maven, Playwright y pruebas del servicio Python al integrar cambios.
5. **Configuración heredada sensible.** El archivo rastreado
   [`application.properties`](../ProAula_merged/aula/src/main/resources/application.properties)
   contiene valores no vacíos en propiedades con nombre de contraseña de base
   de datos y secreto JWT. No se copiaron ni imprimieron esos valores y no se
   confirmó si siguen vigentes. Antes de retirar la carpeta, comprobarlos en un
   entorno seguro; si fueron credenciales reales, rotarlas y revisar el
   historial del repositorio. No desplegar la copia heredada.

## Limpieza: decisión recomendada

### Limpieza segura ya aplicada

- Retirada del bloque Maven que copiaba un XML no existente. No afecta las
  fuentes de recursos activas (`src/main/resources`).

### Candidatos; no borrar sin confirmación adicional

| Candidato | Evidencia actual | Acción segura |
| --- | --- | --- |
| `aula/src/main/resources/static/css/style_2.css` | Sin referencia directa por nombre en las fuentes escaneadas. | Revisar enlaces externos, páginas antiguas y despliegues antes de borrar. |
| `aula/src/main/resources/static/js/lista_mensajes.js` | Sin inclusión activa detectada; sí aparece como destino de la herramienta de mantenimiento. | Decidir si se conserva esa herramienta; comprobar URLs guardadas/compartidas. |
| `Bus_icono.png`, `escudo.png`, `logocompleto.jpeg`, `logocompleto_sin fondo.png` | Sin referencias directas por nombre detectadas. | Confirmar consumo externo o histórico antes de eliminar. |
| `aula/scripts/maintenance/replace_inline_asset_refs.py` | Mapa de páginas de mantenimiento incluye `dashboard-usuario.html`, que no existe en la carpeta actual de plantillas. | Actualizar el mapa o retirar la herramienta si ya no se usa; no ejecutarla a ciegas sobre plantillas. |
| `ProAula_merged/` | No es el build activo; el Dockerfile carece del `pom.xml` que intenta copiar y la configuración contiene valores sensibles no vacíos. | Respaldar, verificar que no haya proceso externo que lo consuma, sanear/rotar secretos y retirar la copia completa en un cambio separado. |
| `graphql-client/` vacío | Sin contenido funcional rastreado ni referencias de GraphQL. | Puede omitirse; su borrado local no cambia el runtime. |

No se detectaron duplicados exactos por SHA-256 entre los recursos estáticos
comparados. No se recomienda borrar dependencias por la salida automática de
`mvn dependency:analyze`: starters, dependencias runtime y usos por reflexión
pueden parecer sin uso directo.

### Mantener

- `ModelosRepo/artifacts/delay_risk.joblib`: la API lo carga directamente.
- Los CSV sintéticos y el generador: sirven para reproducir entrenamiento y
  métricas; no se comprobó todavía que regenerar produzca los mismos archivos
  byte a byte.
- Migraciones SQL: Flyway está deshabilitado en producción, pero hay migraciones
  usadas por procedimientos manuales/bootstrap y documentación. No es seguro
  borrarlas por estar deshabilitadas automáticamente.
- Shapefile fuente y generador cartográfico: conservan la procedencia de los
  recursos usados por mapas.
- `target/`, `node_modules/`, `__pycache__/`, `*.pyc`, resultados de Playwright y
  logs locales: son regenerables/caché; mantenerlos ignorados, no versionarlos.

## Mejoras de rendimiento justificadas y cómo comprobarlas

1. **Caché Thymeleaf en producción.** `spring.thymeleaf.cache` está en `false`
   en la configuración común y no se activa en el perfil `prod`. Probar
   `true` solo en producción; mantenerlo desactivado localmente para desarrollo.
   Comparar antes/después con la misma carga y rutas: latencia p50/p95, CPU,
   throughput y memoria. No se afirma una ganancia medida todavía.
2. **Evitar entrenamiento en la primera petición.** `get_delay_risk_model()`
   entrena sintéticamente si falta el artefacto. El despliegue debe incluir y
   verificar el artefacto durante build/readiness, para no pagar un costo de
   entrenamiento en tiempo de servicio.
3. **Automatizar pruebas Python dentro del repo.** Recuperar/agregar fuentes de
   pruebas en `ModelosRepo/tests/`, definir un comando ejecutable desde la raíz
   del servicio y añadirlo al CI junto con Maven y Playwright.
4. **No ajustar Hikari ni minificar recursos sin mediciones.** El pool tiene
   límites configurados, pero no hay evidencia de saturación ni benchmark que
   justifique un tamaño distinto. No se encontraron duplicados exactos de
   CSS/JS/imagen.

## Evidencia de validación disponible

El [informe de runtime](./runtime-validation-report.md) registra 186 pruebas
Java, 37 pruebas Playwright, smoke tests de la API Python y 43 aserciones del
planificador de mapas aprobadas. Esos resultados históricos tienen límites:
H2 en vez de MySQL/TiDB, pruebas Python fuera del repositorio y E2E de modelos
con respuesta interceptada. Después de retirar el recurso muerto de `pom.xml`,
`.\mvnw.cmd test -q` terminó con código 0: 186 pruebas, 0 fallas, 0 errores y
0 omitidas. También se comprobaron los enlaces Markdown y el whitespace de los
12 documentos de `docs/`; no hubo enlaces locales rotos ni espacios finales.
No se realizaron cambios al código funcional en esta auditoría.
