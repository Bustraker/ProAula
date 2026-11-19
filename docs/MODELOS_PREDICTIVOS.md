# Modelos predictivos de retraso de Bustraker

## Resumen ejecutivo

El modelo principal es un clasificador supervisado que estima el riesgo de que un
viaje presente un retraso mayor de diez minutos. Una regresión logística se usa
únicamente como línea base de comparación; no es el modelo desplegado.

| Objetivo | Método actual | Ubicación principal | Aprende de datos |
| --- | --- | --- | --- |
| Riesgo de retraso mayor de 10 minutos | `HistGradientBoostingClassifier` | `Modelos/proaula_models/delay_risk.py` | Sí |
| Línea base de riesgo | `LogisticRegression` | `Modelos/train_models.py` | Sí, solo comparación |

Actualmente los datos de entrenamiento y evaluación son sintéticos. Por tanto,
los resultados son una demostración técnica y no se pueden presentar como
desempeño real sobre la operación de Cartagena.

## Variable objetivo y predictoras

| Modelo o método | Variable objetivo (target) | Variables predictoras o entradas | Naturaleza |
| --- | --- | --- | --- |
| `HistGradientBoostingClassifier` para riesgo de retraso | `retraso_mayor_10_min`: `1` si `retraso_minutos > 10`; `0` si es menor o igual a 10 | `ruta`, `dia_semana`, `hora_salida`, `duracion_planificada_min`, `indice_demanda_pronosticada` | Clasificación supervisada; es el clasificador principal |
| `LogisticRegression` de comparación | La misma etiqueta `retraso_mayor_10_min` | Las mismas cinco variables predictoras del clasificador principal | Clasificación supervisada; línea base, no es el modelo desplegado |

En los dos clasificadores, `fecha_viaje` sirve para ordenar la separación
temporal y derivar `dia_semana`; **no** entra como predictor. Tampoco entran
`retraso_minutos` como predictor, `escenario_sintetico`, clima observado,
tráfico real, estado del bus ni coordenadas GPS. `retraso_minutos` se usa para
construir la etiqueta cuando el CSV no la entrega directamente.

## 1. Riesgo de retraso

### Método

El método principal es un clasificador binario
`HistGradientBoostingClassifier` de scikit-learn. La clase positiva significa
`retraso_mayor_10_min = 1`; la negativa significa retraso menor o igual a diez
minutos.

Se compara con una regresión logística. Esta última es solo una línea base:
normaliza variables numéricas y convierte variables categóricas en columnas
binarias. No reemplaza al modelo entregado.

### Variable objetivo y predictoras

- **Objetivo (`y`)**: `retraso_mayor_10_min`, binario. Vale `1` cuando
  `retraso_minutos > 10`; vale `0` cuando el retraso es de 10 minutos o menos.
- **Predictoras (`X`)**:
  - `ruta`: categoría de ruta; se codifica con `OneHotEncoder`.
  - `dia_semana`: día derivado de `fecha_viaje`; se codifica con `OneHotEncoder`.
  - `hora_salida`: hora entera entre 0 y 23.
  - `duracion_planificada_min`: duración mayor que 0 y de hasta 240 minutos.
  - `indice_demanda_pronosticada`: índice entre 0 y 100.

La ruta y el día se codifican como categorías. Las variables numéricas pasan
directamente al clasificador. El modelo no recibe clima, tráfico real, accidentes,
estado del bus o datos GPS históricos.

La regresión logística de línea base compara el mismo `y` con el mismo `X`;
codifica las categorías con `OneHotEncoder` y estandariza las variables numéricas
con `StandardScaler`. Solo se usa para comparación en la evaluación.

### Ubicación y papel de cada archivo

- `Modelos/proaula_models/delay_risk.py`: define variables, validación, pipeline,
  entrenamiento, umbral, inferencia y carga del artefacto.
- `Modelos/proaula_models/synthetic_data.py`: genera el conjunto sintético de
  retrasos y los escenarios de estrés del clasificador.
- `Modelos/train_models.py`: genera datos, entrena el HistGradientBoosting,
  entrena y compara la regresión logística, elige el umbral, evalúa escenarios,
  calcula métricas de los clasificadores y guarda los artefactos.
- `Modelos/proaula_models/api.py`: expone la predicción de riesgo y sus métricas
  mediante FastAPI y Pydantic.
- `Modelos/artifacts/delay_risk.joblib`: contiene el modelo entrenado cargado por
  la API.
- `Modelos/artifacts/evaluation.json`: contiene métricas de clasificación,
  umbral, origen y distribución de los datos.
- `Modelos/modelo_riesgo_retraso_transporte.ipynb`: demostración interactiva del
  clasificador y su evaluación.
- `Modelos/tests/test_models.py`: pruebas del entrenamiento y validación del
  clasificador de retrasos.
- `aula/src/main/java/com/proaula/aula/dto/modelos/RiesgoRetrasoRequest.java`:
  contrato Java de entrada.
- `RiesgoRetrasoResponse.java`: contrato Java de salida.
- `aula/src/main/java/com/proaula/aula/Service/ModelosPredictivosService.java`:
  envía la solicitud a FastAPI y procesa errores 503 o 400.
- `aula/src/main/java/com/proaula/aula/Controller/ModelosPredictivosRestController.java`:
  expone el endpoint `/api/modelos/retrasos/riesgo` a Spring.

### Pipeline

El pipeline combina preprocesamiento y clasificación:

```text
OneHotEncoder(categorías) + paso directo(numéricas)
                    -> HistGradientBoostingClassifier
                    -> probabilidad de clase positiva
                    -> comparación con umbral
                    -> BAJO o ALTO
```

Se usa `OneHotEncoder(handle_unknown="ignore")`, por lo que rutas o categorías
no vistas no deben provocar una excepción. Sin embargo, no recibirán efectos
aprendidos hasta contar con datos reales suficientes.

### Entrenamiento y umbral

Los datos se separan cronológicamente en entrenamiento, validación y prueba.
El umbral se ajusta únicamente en la validación para maximizar F1. Después se
ajusta el modelo final con entrenamiento y validación y se evalúa una vez sobre
prueba posterior. El holdout no se usa para elegir el umbral.

La decisión es:

```text
probabilidad >= umbral  -> ALTO
probabilidad < umbral   -> BAJO
```

`ALTO` significa alerta según la probabilidad y el umbral elegido; no significa
que el retraso esté confirmado.

### Datos históricos reales

`train_models.py` puede cargar un CSV mediante `PROAULA_DELAY_DATASET`. El contrato
espera al menos:

```text
fecha_viaje,ruta,hora_salida,duracion_planificada_min,
indice_demanda_pronosticada,retraso_minutos
```

También puede aceptar `retraso_mayor_10_min`. Si existen ambas etiquetas,
deben coincidir con `retraso_minutos > 10`. Cada partición temporal debe tener
las dos clases y el identificador de ruta debe coincidir con el enviado por la
aplicación.

## Dónde ver el resultado en el frontend

La predicción del clasificador se muestra en la página **Planear viaje**, tanto
para usuarios autenticados ([viajar.html](../aula/src/main/resources/templates/Usuario/viajar.html))
como para visitantes ([viajar_public.html](../aula/src/main/resources/templates/Usuario/viajar_public.html)).
Después de planificar y abrir el detalle de una opción, cada tramo muestra el
nivel y porcentaje de riesgo de retraso mayor de diez minutos, el umbral aplicado
y el origen de los datos. La interfaz se actualiza desde
[map-travel.js](../aula/src/main/resources/static/js/map-travel.js).

Con la configuración actual, el origen se indica como demostración con datos
sintéticos. Si el servicio no responde, la interfaz muestra **Predicción no
disponible**; no sustituye el resultado por un valor inventado. La regresión
logística es una línea base de evaluación y no aparece como resultado separado
en el frontend.

## Entrenamiento y ejecución

Desde `Modelos`:

```powershell
& '..\.venv\Scripts\python.exe' train_models.py
& '..\.venv\Scripts\python.exe' -m uvicorn proaula_models.api:app `
  --host 127.0.0.1 --port 8000
```

El archivo `train_models.py` crea los datos sintéticos si no se proporciona
`PROAULA_DELAY_DATASET`, compara el clasificador principal con la regresión
logística, calcula métricas y escribe:

- `artifacts/delay_risk.joblib`
- `artifacts/evaluation.json`
- archivos CSV sintéticos en `data/`

Para entrenar con datos históricos:

```powershell
$env:PROAULA_DELAY_DATASET = 'C:\datos\viajes_historicos.csv'
& '..\.venv\Scripts\python.exe' train_models.py
```

Después reinicia la API porque el modelo se conserva en caché durante el proceso.
