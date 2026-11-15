# Mapa de carpetas y archivos de ProAula

Este mapa describe la estructura activa del repositorio. La aplicación principal
es [`aula/`](../aula/), pero el repositorio también contiene el servicio Python de
modelos, los datos geográficos y los documentos del proyecto.

## Carpetas de la raíz del repositorio

| Carpeta | Propósito |
| --- | --- |
| [`.github/`](../.github/) | Configuración y artefactos de automatización y modernización del repositorio. No contiene código que se ejecute al iniciar la aplicación. |
| [`.vscode/`](../.vscode/) | Configuración de VS Code. El lanzamiento y la tarea de compilación apuntan al proyecto Maven en `aula/`. |
| [`aula/`](../aula/) | Aplicación Spring Boot activa: backend Java, páginas web, recursos estáticos, pruebas y scripts de soporte. |
| [`data/`](../data/) | Datos fuente que no pertenecen a los recursos Java. Incluye la cartografía original de barrios. |
| [`docs/`](./) | Guías e informes centralizados. `docs/logs/` contiene salidas locales generadas y está excluido de Git. |
| [`ModelosRepo/`](../ModelosRepo/) | Servicio FastAPI de predicción/recomendación, biblioteca Python, modelo entrenado y datos sintéticos. |
| [`ProAula_merged/`](../ProAula_merged/) | Copia heredada de integración. Su Dockerfile espera un `pom.xml` en ese contexto, pero ese archivo no está ahí; no es una ruta de compilación válida ni el módulo Maven activo. El código principal es `aula/`. |

## Aplicación Spring Boot: `aula/`

### Archivos de entrada en la raíz

Estos archivos permanecen en la raíz deliberadamente:

| Archivo o carpeta | Propósito |
| --- | --- |
| [`pom.xml`](../aula/pom.xml) | Define el proyecto Maven, dependencias, plugins y compilación Spring Boot. Maven lo busca en la raíz del módulo. |
| [`mvnw`](../aula/mvnw) y [`mvnw.cmd`](../aula/mvnw.cmd) | Lanzadores Maven para Unix y Windows; arrancan desde el módulo y usan `.mvn/wrapper/`. |
| [`.mvn/`](../aula/.mvn/) | Configuración del Maven Wrapper, incluida la versión de Maven que descargará. |
| [`Dockerfile`](../aula/Dockerfile) y [`.dockerignore`](../aula/.dockerignore) | Construcción de la imagen del backend desde el contexto `aula/`. |
| [`.gitignore`](../aula/.gitignore) | Excluye secretos locales, resultados de compilación y otros archivos generados. |
| [`.gitattributes`](../aula/.gitattributes) | Normaliza atributos de archivos, como los finales de línea. |

`pom.xml`, `mvnw` y `mvnw.cmd` son parte del mismo flujo de construcción, pero
Maven/Spring Boot esperan sus puntos de entrada en la raíz del módulo. Moverlos a
una carpeta `build/` requeriría cambiar el directorio base de Maven, configurar
rutas personalizadas para fuentes y recursos y adaptar Docker, VS Code y los
scripts de ejecución. Por eso se mantienen en la ubicación convencional; las
herramientas de soporte sí están agrupadas en `scripts/`.

### Código y recursos

| Ruta | Contenido |
| --- | --- |
| [`src/main/java/com/proaula/aula/`](../aula/src/main/java/com/proaula/aula/) | Código Java de la aplicación. |
| [`Barrios/`](../aula/src/main/java/com/proaula/aula/Barrios/) | Clases auxiliares para el dominio y procesamiento de barrios. |
| [`config/`](../aula/src/main/java/com/proaula/aula/config/) | Configuración de Spring, seguridad, autenticación e integraciones. |
| [`Controller/`](../aula/src/main/java/com/proaula/aula/Controller/) | Controladores web y endpoints HTTP. |
| [`Service/`](../aula/src/main/java/com/proaula/aula/Service/) | Reglas de negocio y coordinación de servicios. |
| [`Repository/`](../aula/src/main/java/com/proaula/aula/Repository/) | Acceso a datos y repositorios. |
| [`Entity/`](../aula/src/main/java/com/proaula/aula/Entity/) | Entidades persistidas en la base de datos. |
| [`dto/`](../aula/src/main/java/com/proaula/aula/dto/) | Objetos de intercambio para solicitudes y respuestas; `dto/modelos/` contiene contratos relacionados con predicciones. |
| [`exception/`](../aula/src/main/java/com/proaula/aula/exception/) | Excepciones y tratamiento de errores de la aplicación. |
| [`src/main/resources/`](../aula/src/main/resources/) | Configuración, plantillas, recursos web y archivos de datos cargados por Spring. |
| [`static/`](../aula/src/main/resources/static/) | Archivos públicos servidos tal cual: `css/`, `js/`, `images/` y `data/`. `data/cartografia/` contiene recursos de mapas y su atribución. |
| [`templates/`](../aula/src/main/resources/templates/) | Vistas del servidor: `Admin/`, `Usuario/` y `error/`. |
| [`db/`](../aula/src/main/resources/db/) | `migration/mysql/` contiene migraciones SQL y `seed/` contiene datos/esquemas auxiliares. |
| [`resources/data/`](../aula/src/main/resources/data/) | Archivos de datos internos del classpath, distintos de los recursos públicos bajo `static/`. |
| [`graphql-client/`](../aula/src/main/resources/graphql-client/) | Carpeta actualmente vacía. No hay dependencias, configuración ni código GraphQL que la utilicen; parece un espacio reservado para operaciones o documentos de cliente si se incorpora GraphQL en el futuro. |
| [`application-local.yml`](../aula/src/main/resources/application-local.yml) y perfiles asociados | Configuración de Spring por entorno; los secretos se proporcionan mediante variables de entorno, no se escriben en el YAML. |

No mover `src/main/java`, `src/main/resources/static` ni `templates`: son las
ubicaciones convencionales que usa Spring Boot y las que el proyecto conserva.

### Pruebas y herramientas

| Ruta | Propósito |
| --- | --- |
| [`src/test/java/`](../aula/src/test/java/) | Pruebas JUnit de servicios, controladores, persistencia y flujos completos. Sus carpetas `Barrios/`, `config/`, `Controller/`, `Entity/`, `exception/`, `flows/`, `Repository/` y `Service/` agrupan pruebas por el mismo tipo de componente que en `src/main/java/`. |
| [`src/test/resources/`](../aula/src/test/resources/) | Configuración y recursos exclusivos de pruebas. |
| [`e2e/`](../aula/e2e/) | Suite de navegador Playwright, dependencias, configuración y servidor de prueba. `e2e/tests/` agrupa los flujos y `tests/helpers/` sus auxiliares. |
| [`scripts/local/`](../aula/scripts/local/) | Lanzadores HTTP/HTTPS y preparación del entorno OAuth local. |
| [`scripts/database/`](../aula/scripts/database/) | Migrador MySQL–TiDB, sus dependencias y certificado CA; también SQL manual de normalización de roles. |
| [`scripts/maps/`](../aula/scripts/maps/) | Generador de datos cartográficos y prueba del planificador de rutas del mapa. |
| [`scripts/maintenance/`](../aula/scripts/maintenance/) | Herramientas de mantenimiento de plantillas y referencias a recursos. |
| [`target/`](../aula/target/) | Salida generada por Maven (clases, reportes y JAR). Se puede regenerar; no es código fuente. |
| [`.github/`](../aula/.github/) | Metadatos y registros de herramientas de modernización; no intervienen en el arranque de Spring. |

## Servicio predictivo: `ModelosRepo/`

| Ruta | Propósito |
| --- | --- |
| [`api/`](../ModelosRepo/api/) | Aplicación FastAPI y contratos HTTP: salud, métricas, predicción de riesgo y recomendaciones. |
| [`proaula_models/`](../ModelosRepo/proaula_models/) | Biblioteca Python reutilizable de modelos y lógica de recomendación. |
| [`artifacts/`](../ModelosRepo/artifacts/) | Modelo entrenado serializado que carga el servicio. |
| [`data/`](../ModelosRepo/data/) | Escenarios y conjuntos sintéticos para entrenamiento/evaluación. |
| [`tools/`](../ModelosRepo/tools/) | Herramientas de entrenamiento y mantenimiento de modelos. |
| [`tests/`](../ModelosRepo/tests/) | Ubicación reservada para pruebas Python. Actualmente solo hay bytecode `__pycache__`; no hay archivos fuente `test_*.py` versionados. |
| [`requirements.txt`](../ModelosRepo/requirements.txt) | Dependencias Python del servicio y las herramientas. |

`__pycache__/` y `*.pyc` son caché de Python, no fuentes ni pruebas ejecutables
que deban editarse manualmente.

## Datos de mapas: `data/`

[`data/maps/source/Barrios_Ctg/`](../data/maps/source/Barrios_Ctg/) conserva los
archivos fuente del shapefile. El generador está en
[`aula/scripts/maps/generate_barrios_shapefile.py`](../aula/scripts/maps/generate_barrios_shapefile.py)
y el JSON utilizado por la aplicación se mantiene en
[`aula/src/main/resources/data/`](../aula/src/main/resources/data/), para no
cambiar la estructura de recursos de Spring Boot.

## Carpetas generadas o auxiliares

- `node_modules/`, `e2e/test-results/` y `e2e/playwright-report/` son salidas o
  dependencias de Playwright; se regeneran con npm/Playwright.
- `__pycache__/` y `*.pyc` son caché de Python.
- `aula/target/` es salida Maven.
- `docs/logs/` conserva logs locales de diagnóstico y se excluye de Git.
- `.git/` es el historial interno de Git; no forma parte del código de la
  aplicación.
