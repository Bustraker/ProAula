# Modelos predictivos y recomendaciones de Bustraker

## Resumen ejecutivo

El proyecto tiene un clasificador aprendido para estimar retrasos y dos métodos
deterministas de recomendación implementados en Python. También existe un índice
explicable de prioridad de cobertura implementado directamente en Java. Ese índice
no es una predicción ni usa aprendizaje automático.

| Objetivo | Método actual | Ubicación principal | Aprende de datos |
| --- | --- | --- | --- |
| Riesgo de retraso mayor de 10 minutos | `HistGradientBoostingClassifier` | `Modelos/proaula_models/delay_risk.py` | Sí |
| Línea base de riesgo | `LogisticRegression` | `Modelos/train_models.py` | Sí, solo comparación |
| Elegir entre rutas | Ordenamiento por costo generalizado | `Modelos/proaula_models/recommendations.py` | No |
| Sugerir destinos | Puntaje por frecuencia y recencia | `Modelos/proaula_models/recommendations.py` | No todavía |
| Priorizar cobertura de barrios | Índice ponderado explicable | `aula/src/main/java/com/proaula/aula/Service/ModeloPrioridadCoberturaService.java` | No |

Actualmente los datos de entrenamiento y evaluación son sintéticos. Por tanto,
los resultados son una demostración técnica y no se pueden presentar como
desempeño real sobre la operación de Cartagena.

## 1. Riesgo de retraso

### Método

El método principal es un clasificador binario
`HistGradientBoostingClassifier` de scikit-learn. La clase positiva significa
`retraso_mayor_10_min = 1`; la negativa significa retraso menor o igual a diez
minutos.

Se compara con una regresión logística. Esta última es solo una línea base:
normaliza variables numéricas y convierte variables categóricas en columnas
binarias. No reemplaza al modelo entregado.

### Variables

- `ruta`: variable categórica. Se transforma con `OneHotEncoder`.
- `dia_semana`: variable categórica. Se transforma con `OneHotEncoder`.
- `hora_salida`: número entero de 0 a 23.
- `duracion_planificada_min`: duración prevista, mayor que cero y máxima de 240.
- `indice_demanda_pronosticada`: índice de 0 a 100.

La ruta y el día se codifican como categorías. Las variables numéricas pasan
directamente al clasificador. El modelo no recibe clima, tráfico real, accidentes,
estado del bus o datos GPS históricos.

### Ubicación y papel de cada archivo

- `Modelos/proaula_models/delay_risk.py`: define variables, validación, pipeline,
  entrenamiento, umbral, inferencia y carga del artefacto.
- `Modelos/proaula_models/synthetic_data.py`: genera el conjunto sintético de
  retrasos y los escenarios de estrés.
- `Modelos/train_models.py`: genera datos, entrena el HistGradientBoosting,
  entrena y compara la regresión logística, elige el umbral, evalúa escenarios,
  evalúa rutas y destinos y guarda los artefactos.
- `Modelos/proaula_models/api.py`: expone la predicción y las métricas mediante
  FastAPI y Pydantic.
- `Modelos/artifacts/delay_risk.joblib`: contiene el modelo entrenado cargado por
  la API.
- `Modelos/artifacts/evaluation.json`: contiene métricas, umbral, escenario,
  distribución de datos y evaluaciones.
- `Modelos/modelo_riesgo_retraso_transporte.ipynb`: demostración interactiva del
  clasificador y su evaluación.
- `Modelos/tests/test_models.py`: pruebas del entrenamiento, validación y
  recomendaciones.
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

## 2. Recomendación de rutas

### Método

No usa un modelo entrenado. Usa ordenamiento por costo generalizado:

```text
tiempo generalizado =
ETA + (30 / salidas_por_hora) + demora_clima_min
```

La espera se aproxima como la mitad del intervalo entre salidas:
`30 / salidasPorHora`. El método conserva la ruta habitual si ninguna alternativa
ahorra al menos cinco minutos respecto de ella.

### Justificación

- El ETA ya debe incluir tráfico; no se suma `congestionPct` otra vez.
- El umbral de cinco minutos evita cambios que sean visibles pero poco útiles.
- El resultado es explicable: indica ruta recomendada, costo y motivo.
- No usa el historial real de llegada de todos los itinerarios.

### Archivos

- `Modelos/proaula_models/recommendations.py`: calcula costo, ordena rutas y aplica
  el umbral de ahorro.
- `Modelos/proaula_models/api.py`: valida las opciones y expone
  `POST /v1/recommendations/routes`.
- `Modelos/train_models.py`: evalúa el método sintético frente a un oráculo.
- `Modelos/modelo_recomendacion_rutas.ipynb`: muestra ejemplos de ruta.
- Los DTO Java `RecomendacionRutaRequest.java` y
  `RecomendacionRutaResponse.java` conectan el servicio Python con Spring.

El planificador principal de la interfaz todavía usa directitud, distancia y
paradas antes del endpoint predictivo. La API de rutas no reemplaza esa lógica
porque no existen frecuencias ni ETA reales completos para todos los recorridos.

## 3. Sugerencias de destinos

### Método

No usa clasificación ni entrenamiento automático. Filtra el catálogo por nombre,
tipo y barrio que coincidan con la consulta. Después calcula una estimación
semanal de visitas con frecuencia y recencia:

```text
frecuencia reciente = visitas_7d / 7
frecuencia mensual = visitas_30d / 30

estimación =
7 *
(0.05 * frecuencia reciente + 0.95 * frecuencia mensual) *
(0.9 + 0.2 * exp(-dias_desde_ultima_visita / 14))
```

Cuanto más recientes son las visitas, mayor es el factor de recencia. Los
destinos sin coincidencia no se agregan. Los destinos sin historial se ordenan
por nombre cuando tienen la misma puntuación.

### Importante

El método no predice usuarios reales ni visitas futuras observables. Solo combina
el historial enviado por la aplicación con el catálogo proporcionado. Los
favoritos o barrios guardados no equivalen todavía a un historial completo de
visitas.

### Archivos

- `Modelos/proaula_models/recommendations.py`: filtrado, ponderación y ordenamiento.
- `Modelos/proaula_models/api.py`: valida catálogo e historial y expone
  `POST /v1/recommendations/destinations`.
- `Modelos/train_models.py`: evalúa una implementación sintética contra una
  referencia de popularidad reciente.
- `Modelos/modelo_sugerencias_destinos.ipynb`: explica y muestra ejemplos.
- Los DTO Java `SugerenciasDestinoRequest.java` y
  `SugerenciasDestinoResponse.java` transportan la solicitud y respuesta.

## 4. Prioridad de cobertura de barrios

Este análisis está implementado en Java y no es un modelo predictivo.

### Método

Calcula un índice de 0 a 100:

- Hasta 40 puntos por población relativa.
- 40 puntos si el barrio no tiene rutas activas verificadas.
- 20 puntos si tiene exactamente una ruta activa verificada.
- 20 puntos si no tiene paradas activas georreferenciadas.
- 10 puntos si tiene paradas activas, pero ninguna coordenada válida.
- Sin puntos adicionales cuando existen varias rutas y paradas con ubicación.

Niveles:

```text
ALTA  -> 60 a 100
MEDIA -> 35 a 59
BAJA  -> 0 a 34
```

Es una puntuación de revisión de cobertura. No mide demanda, frecuencia de
pasajeros, calidad del servicio ni probabilidad de inversión.

### Archivos

- `aula/src/main/java/com/proaula/aula/Service/ModeloPrioridadCoberturaService.java`:
  calcula rutas, paradas y puntos.
- `aula/src/main/java/com/proaula/aula/Service/BarrioContextoService.java`: entrega
  población y contexto geográfico.
- `aula/src/main/java/com/proaula/aula/Repository/RutaRepository.java`: consulta
  rutas verificadas.
- `aula/src/main/java/com/proaula/aula/dto/modelos/PrioridadCoberturaResponse.java`
  y sus registros internos: forman la respuesta explicable.

## 5. Llegadas por GPS

Las llegadas cercanas no son una predicción aprendida. Son reportes de ubicación
voluntarios, recientes y temporales. Vencen aproximadamente a los 90 segundos y
no se guardan como etiquetas de entrenamiento.

Los componentes principales están en `LlegadaBusRestController.java`,
`LlegadaBusService.java` y el JavaScript del mapa. Su precisión depende de la
participación de los usuarios y no debe confundirse con el modelo de retrasos.

## Entrenamiento y ejecución

Desde `Modelos`:

```powershell
& '..\.venv\Scripts\python.exe' train_models.py
& '..\.venv\Scripts\python.exe' -m uvicorn proaula_models.api:app `
  --host 127.0.0.1 --port 8000
```

El archivo `train_models.py` crea los datos sintéticos si no se proporciona
`PROAULA_DELAY_DATASET`, compara modelos, calcula métricas y escribe:

- `artifacts/delay_risk.joblib`
- `artifacts/evaluation.json`
- archivos CSV sintéticos en `data/`

Para entrenar con datos históricos:

```powershell
$env:PROAULA_DELAY_DATASET = 'C:\datos\viajes_historicos.csv'
& '..\.venv\Scripts\python.exe' train_models.py
```

Después reinicia la API porque el modelo se conserva en caché durante el proceso.
