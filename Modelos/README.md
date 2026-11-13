# Modelos de movilidad

Este directorio contiene los modelos reutilizables, sus notebooks, datos
sintéticos versionados, artefactos entrenados, evaluación temporal y una API HTTP
integrada con Spring Boot.

## Enfoque por objetivo

| Objetivo | Enfoque | Variables o señales |
| --- | --- | --- |
| Riesgo de retraso mayor a 10 minutos | `HistGradientBoostingClassifier`; se compara con una regresión logística de referencia | ID de ruta, día de semana, hora, duración planificada e índice de demanda |
| Recomendación de ruta | Ordenamiento por tiempo generalizado | ETA, espera estimada entre salidas y demora climática |
| Sugerencias de destinos | Estimación de visitas esperadas en los próximos siete días | Coincidencia de búsqueda, frecuencia reciente y tiempo desde la última visita |

La regresión logística se evalúa como línea base, no como regresión lineal para
predecir una cantidad. Para la selección de rutas se usa ordenamiento, no un
clasificador: la espera se estima como la mitad del intervalo entre salidas
(`30 / salidasPorHora`) y se conserva la ruta habitual si una alternativa no
ahorra al menos cinco minutos.

## Datos generados y limitaciones

Los datos actuales son sintéticos y reproducibles; no son registros de usuarios,
buses ni viajes reales. Se generan porque la aplicación todavía no cuenta con
historial etiquetado suficiente de retrasos y visitas. Los CSV quedan en `data/`;
el conjunto de riesgo de entrenamiento contiene seis perfiles de operación y
cada fila incluye el escenario como metadato de evaluación (no como variable de
entrada del clasificador), su partición temporal y el origen
`SINTETICOS_DEMOSTRACION`. Se genera también un conjunto independiente de estrés
con mayor peso en escenarios difíciles; no participa en el entrenamiento ni en
el ajuste del umbral.

El clasificador se guarda en `artifacts/delay_risk.joblib`; el informe está en
`artifacts/evaluation.json`. El ID de ruta se aprende como variable categórica;
las rutas desconocidas en inferencia se aceptan mediante `handle_unknown="ignore"`,
pero no aportan un efecto específico hasta que existan viajes etiquetados para
ellas. Para entrenar y evaluar efectos de rutas reales, los IDs del CSV deben
coincidir con los que envía la aplicación.

La API de sugerencias de destinos opera sobre el catálogo y el historial recibidos
en cada solicitud. El planificador actual prioriza barrios incluidos en trayectos
favoritos guardados por el usuario; eso refleja preferencias explícitas, no un
registro completo de visitas ni una predicción aprendida. El catálogo no se
inventa cuando falta historial. Las estimaciones de ruta y destino no se deben
presentar como tiempos o visitas reales.

### Evaluación reproducible

Evaluación determinista obtenida al ejecutar `train_models.py`:

| Modelo/objetivo | Conjunto de prueba | Resultados |
| --- | --- | --- |
| HistGradientBoosting, riesgo de retraso | 7.824 viajes posteriores; prevalencia 24,1 % | Accuracy 0,808; balanced accuracy 0,787; precision 0,580; recall 0,745; F1 0,652; F2 0,705; ROC-AUC 0,876; precisión promedio 0,720 (2,98× la prevalencia); Brier 0,115 |
| Regresión logística, referencia | Mismo corte temporal | Accuracy 0,787; balanced accuracy 0,769; precision 0,543; recall 0,735; F1 0,624; F2 0,686; ROC-AUC 0,858; precisión promedio 0,679; Brier 0,123 |
| Selección de ruta | 5.000 escenarios generados | Coincidencia con la decisión de referencia 81,9 %; arrepentimiento medio 0,59 min; cambio a una ruta que no mejora 4,0 % |
| Recomendación de destino | 498 usuarios retenidos; 24 candidatos por usuario | Precisión@5 0,714; NDCG@5 0,831. Una referencia de popularidad reciente alcanza 0,716 y 0,831, respectivamente |

El conjunto de entrenamiento distribuye casos entre operación regular (40 %),
hora pico (15 %), demanda alta (10 %), demanda baja (10 %), duración extensa
(10 %) y fin de semana pico (15 %). El archivo de estrés usa una mezcla distinta y
evalúa el modelo sin reajustarlo:

| Escenario de estrés | Casos | Prevalencia sintética | F1 | Recall | ROC-AUC |
| --- | ---: | ---: | ---: | ---: | ---: |
| Demanda alta | 2.381 | 41,8 % | 0,694 | 0,860 | 0,785 |
| Demanda baja | 1.207 | 0,0 % | 0,000 | 0,000 | No definido: no hubo positivos |
| Duración extensa | 2.418 | 63,2 % | 0,860 | 0,946 | 0,898 |
| Fin de semana pico | 2.364 | 29,9 % | 0,561 | 0,704 | 0,728 |
| Hora pico | 2.448 | 22,1 % | 0,515 | 0,553 | 0,771 |
| Operación regular | 1.182 | 14,3 % | 0,531 | 0,533 | 0,862 |

Estos resultados describen únicamente la relación artificial usada para generar
los datos; no demuestran exactitud operativa ni calibración sobre viajes reales.
El desempeño más débil en hora pico y fin de semana se deja visible para evitar
ocultarlo en una métrica global. Cuando haya datos reales, la evaluación
cronológica sobre viajes que el modelo no vio debe ser el criterio de aceptación.

El umbral se elige maximizando F1 sobre las probabilidades distintas observadas
en validación (sin una cuadrícula fija que pudiera excluir el óptimo) para
equilibrar precision y recall; el test posterior queda fuera del ajuste. La matriz de
confusión y métricas por clase se guardan en el informe. En el test sintético,
aproximadamente 42 % de las alertas son falsos positivos. Una alternativa basada
en F2 elevaría el recall a costa de más falsas alarmas. La clase `ALTO` no es
garantía; se deben interpretar probabilidad, umbral y métricas juntos.

La matriz de confusión del holdout del clasificador es TN=4.916, FP=1.019,
FN=482 y TP=1.407. El artefacto se ajusta solo con entrenamiento + validación
(32.176 filas); las 7.824 filas de prueba quedan excluidas también del artefacto
para que la estimación reportada mida el mismo modelo entregado.

El dataset sintético incluye efectos de ruta, pero sus códigos `R-01`–`R-06` no
son las rutas de la aplicación. Por eso el artefacto demostrativo no ha aprendido
efectos propios de rutas reales. El modo de entrenamiento histórico carga un CSV
validado y permite aprenderlos cuando contiene los IDs reales y suficientes
viajes etiquetados.

El ranking de destinos tiene precisión@5 alta, pero no supera de forma medible la
referencia de popularidad en el conjunto sintético. No se afirma que la
personalización mejore hasta disponer de datos reales por usuario.

## Ejecutar la API

Desde PowerShell, en la carpeta `Modelos`:

```powershell
& '..\.venv\Scripts\python.exe' -m pip install -r requirements.txt
& '..\.venv\Scripts\python.exe' train_models.py
& '..\.venv\Scripts\python.exe' -m uvicorn proaula_models.api:app --host 127.0.0.1 --port 8000
```

`train_models.py` genera los CSV sintéticos por defecto, compara el clasificador
con su línea base, evalúa el riesgo en seis grupos de estrés independientes,
evalúa las recomendaciones en escenarios retenidos, verifica umbrales de calidad
y escribe el modelo y el informe. Para entrenar el clasificador
con datos históricos, configura `PROAULA_DELAY_DATASET` con la ruta a un CSV antes
de ejecutarlo. La carga valida las columnas, fechas, rangos, etiquetas y
particiones temporales; deriva el día de semana desde la fecha y no copia el CSV
histórico a `data/`. El informe indica por separado el origen de cada dataset.

El CSV histórico debe tener al menos 500 viajes y estas columnas:

```text
fecha_viaje,ruta,hora_salida,duracion_planificada_min,indice_demanda_pronosticada,retraso_minutos
```

`fecha_viaje` debe ser parseable; `hora_salida` es una hora entera de 0 a 23;
duración debe ser mayor que 0 y como máximo 240 minutos; demanda debe estar entre
0 y 100. `retraso_minutos` debe medir el retraso real en minutos; la etiqueta se
define estrictamente como retraso mayor que 10 minutos. En vez de minutos, se
puede suministrar `retraso_mayor_10_min` con valores 0/1; si se incluyen ambos,
deben coincidir; los minutos negativos se permiten para viajes que llegaron
antes de lo programado. Cada partición cronológica debe tener las dos clases y al menos
10 fechas distintas en total. El identificador de `ruta` debe ser el mismo que
envía la API. La duración y demanda deben representar valores disponibles antes
del viaje, no mediciones posteriores; de lo contrario habría fuga de información.
El día de semana se deriva de `fecha_viaje` y se valida para evitar inconsistencias.

Ejemplo PowerShell desde `Modelos`:

```powershell
$env:PROAULA_DELAY_DATASET = 'C:\datos\viajes_historicos.csv'
& '..\.venv\Scripts\python.exe' train_models.py
```

Si no existe el artefacto, la API entrena de forma reproducible el modelo
sintético de demostración al primer uso. Para servir el modelo histórico, ejecutar
el entrenamiento anterior antes de arrancar la API y desplegar juntos el código y
`artifacts/delay_risk.joblib`. Reinicia el servicio después de reemplazar el
artefacto: el modelo se mantiene en caché durante la vida del proceso.

La documentación interactiva queda disponible en `http://127.0.0.1:8000/docs`.
La API está pensada para comunicación interna; no la expongas directamente a
Internet.

## Integración con Spring Boot

El planificador muestra la probabilidad de retraso mayor a diez minutos, el
umbral de alerta y el origen del dataset en cada opción. La hora se toma del reloj
local, la demanda es una aproximación y la duración de referencia se calcula a
partir de barrios y paradas; no incorpora semáforos, tráfico ni retrasos reales.
El planificador muestra además el clima actual y la probabilidad de precipitación
para una coordenada fija del centro de Cartagena obtenidos de Open-Meteo; no envía
la ubicación GPS del usuario a ese proveedor. El clima es contextual y no ajusta
la probabilidad de retraso: no hay datos históricos etiquetados con meteorología
para aprender ni validar ese efecto. Las llegadas por GPS voluntario son dinámicas
y se muestran cuando hay reportes recientes; expiran a los 90 segundos y no se
guardan como historial para entrenamiento.
La API de riesgo está disponible también para la página pública del planificador.
La interfaz consume el riesgo, muestra llegadas GPS disponibles y prioriza
barrios guardados como favoritos. La ruta que encabeza la lista todavía se ordena
por directitud, distancia y paradas; el endpoint de recomendación de rutas no se
usa para cambiar ese orden porque no hay ETA ni frecuencia confiables para todos
los itinerarios. El endpoint de sugerencias de destinos tampoco se conecta como
modelo personalizado: los favoritos guardados no equivalen a un historial
completo de visitas. Por defecto,
Spring Boot busca la API en
`http://localhost:8000`; se puede cambiar con la variable de entorno
`MODELS_API_BASE_URL`.

Los contratos esperan:

- **Riesgo:** `ruta`, `diaSemana`, `horaSalida`, `duracionPlanificadaMin` e
  `indiceDemandaPronosticada`. El identificador de ruta es una variable
  categórica del clasificador.
- **Ruta:** un objeto `habitual` y una lista `alternativas`; cada opción incluye
  `codigo`, `etaMin`, `salidasPorHora`, `demoraClimaMin` y `congestionPct`.
- **Destinos:** `consulta`, `catalogo`, `historial` y `limite`. El catálogo
  contiene elementos con `id`, `nombre`, `tipo` y, opcionalmente, `barrio`.
  Cada elemento del historial incluye `visitas7d`, `visitas30d` y
  `diasDesdeUltimaVisita`.

Si el servicio de modelos no está disponible, Spring Boot responde con un error
`503` explícito; no sustituye la predicción por una respuesta inventada.

## Pruebas

Desde la carpeta `Modelos`:

```powershell
& '..\.venv\Scripts\python.exe' -m unittest discover -s tests -v
```

El notebook de riesgo emplea el mismo artefacto y la misma evaluación. Los
notebooks de rutas y destinos ilustran las recomendaciones; sus ejemplos son
sintéticos y no sustituyen los CSV de evaluación.

Abre los notebooks en Jupyter con `Modelos` como directorio de trabajo para
importar `proaula_models` sin modificar `sys.path`. Para instalar JupyterLab
además de las dependencias de la API, usa `requirements-notebook.txt`.
