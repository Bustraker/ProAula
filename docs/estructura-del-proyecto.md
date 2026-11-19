# Resumen del proyecto Bustraker

Este documento resume la estructura y los componentes principales del proyecto.
La documentación detallada de los métodos predictivos se mantiene en
[MODELOS_PREDICTIVOS.md](./MODELOS_PREDICTIVOS.md).

## Componentes principales

| Carpeta o archivo | Propósito |
| --- | --- |
| `aula/` | Aplicación principal Spring Boot. Contiene backend Java, seguridad, vistas Thymeleaf, recursos web, pruebas y scripts. |
| `aula/src/main/java/com/proaula/aula/` | Controladores, servicios, repositorios, entidades y contratos DTO. |
| `aula/src/main/resources/templates/` | Vistas HTML procesadas por Thymeleaf para usuarios, administradores y páginas públicas. |
| `aula/src/main/resources/static/` | CSS, JavaScript, imágenes y datos públicos. |
| `aula/src/main/resources/application*.yml` | Configuración por entorno de Spring Boot. |
| `aula/pom.xml` | Dependencias, compilación y pruebas Maven. |
| `aula/src/test/java/` | Pruebas unitarias e integración JUnit. |
| `aula/e2e/` | Pruebas de navegador con Playwright. |
| `Modelos/` | Servicio Python FastAPI, entrenamiento, datos sintéticos, artefacto entrenado y notebooks. |
| `docs/` | Documentación resumida del proyecto y de los modelos predictivos. |

## Integración general

El navegador utiliza páginas de Spring Boot. Los controladores Java reciben las
acciones del usuario, consultan la base de datos mediante servicios y repositorios
y representan las vistas Thymeleaf. Las consultas de predicción se hacen por HTTP
desde Spring Boot hacia la API FastAPI ubicada en `Modelos/proaula_models/api.py`.

Spring Boot usa `MODELS_API_BASE_URL`, con `http://localhost:8000` como valor
predeterminado. Si el servicio Python no está disponible, el servicio Java responde
con un error 503 y no reemplaza la predicción por datos inventados.

## Datos y resultados generados

`Modelos/data/` contiene datos sintéticos de demostración. `Modelos/artifacts/`
conserva el clasificador entregado y su informe de evaluación. Las carpetas
`target/`, `node_modules/`, informes de Playwright y cachés Python son resultados
generados y se pueden reconstruir.
