# README – Evidencias de tareas Bustraker

## Propósito
Este documento sirve como evidencia técnica y funcional de las tres tareas definidas para la funcionalidad de planificación de rutas en Bustraker. Tiene como objetivo dejar un registro claro para mover cada tarea por los estados de Jira:
- Por hacer
- En progreso
- En revisión
- Finalizado

## Flujo de evidencia en Jira
Cada tarea debe quedar documentada con estos elementos:
- Nombre de la tarea
- Descripción del objetivo
- Archivos y evidencias técnicas
- Criterios de aceptación
- Estado actual
- Comentario de cierre o revisión

---

## Tarea 1: Lógica de transbordos

### Objetivo
Implementar la lógica de recomendación de rutas con transbordos, priorizando:
1. Ruta directa
2. Menos transbordos
3. Menos barrios intermedios

### Evidencia técnica
- [aula/src/main/resources/static/js/map-travel.js](aula/src/main/resources/static/js/map-travel.js)
- [aula/scripts/test_map_travel.js](aula/scripts/test_map_travel.js)

### Evidencia funcional
En la lógica del planificador se valida que:
- Si existe una ruta directa, se prioriza.
- Si no existe, se recomienda la opción con menos transbordos.
- Se calcula el número de barrios intermedios.
- Se resume el costo del viaje con información como “1 transbordo” o “2 barrios intermedios”.

### Criterios de aceptación
- La aplicación muestra una sola recomendación.
- La recomendación prioriza la ruta directa.
- Si no existe directa, recomienda la opción con menos transbordos.
- El sistema presenta el resumen de costo del viaje.
- Se valida con pruebas automatizadas.

### Estado de Jira
- Por hacer: al iniciar la tarea
- En progreso: cuando se desarrolla la lógica
- En revisión: cuando se valida el comportamiento y pruebas
- Finalizado: cuando queda aprobada la evidencia

---

## Tarea 2: Integración del formulario con el mapa

### Objetivo
Integrar el formulario de origen y destino con la lógica de cálculo de rutas y mostrar el recorrido en el mapa.

### Evidencia técnica
- [aula/src/main/resources/templates/Usuario/viajar.html](aula/src/main/resources/templates/Usuario/viajar.html)
- [aula/src/main/resources/templates/Usuario/viajar_public.html](aula/src/main/resources/templates/Usuario/viajar_public.html)
- [aula/src/main/resources/static/js/map-travel.js](aula/src/main/resources/static/js/map-travel.js)

### Evidencia funcional
La interfaz contiene:
- Campo de origen
- Campo de destino
- Botón Planificar
- Resultado recomendado
- Detalle del tramo
- Mapa con recorrido visual

La lógica correspondiente ejecuta:
- validación de barrios
- recomendación de ruta
- selección del tramo
- renderizado del recorrido en el mapa
- detalle de paradas y transbordos

### Criterios de aceptación
- El usuario puede seleccionar origen y destino.
- El sistema valida opciones válidas.
- La mejor ruta aparece en el panel.
- El recorrido se dibuja en el mapa.
- Se muestran los datos del tramo.
- El flujo funciona en usuario y público.

### Estado de Jira
Esta tarea debe pasar por:
- Por hacer
- En progreso
- En revisión
- Finalizado

---

## Tarea 3: Implementar la API / planificador de rutas

### Objetivo
Dejar la lógica de planificación lista para ser consumida como servicio o flujo centralizado del sistema, con la recomendación de rutas y transbordos.

### Evidencia técnica
- [aula/src/main/resources/static/js/map-travel.js](aula/src/main/resources/static/js/map-travel.js)
- [aula/scripts/test_map_travel.js](aula/scripts/test_map_travel.js)

### Evidencia funcional
La lógica de planificador está centralizada en la aplicación para:
- definir origen y destino
- buscar combinaciones de rutas
- ordenar por directos, transbordos y barrios intermedios
- construir el itinerario recomendado
- mostrar la ruta seleccionada y su detalle

### Criterios de aceptación
- El sistema puede planificar una ruta a partir de origen y destino.
- El algoritmo prioriza directos y luego menos transbordos.
- Se calcula el itinerario completo.
- Se deja evidencia de pruebas.
- La funcionalidad es reutilizable en la capa de presentación.

### Estado de Jira
- Por hacer: al crear la tarea
- En progreso: cuando se está implementando la lógica
- En revisión: cuando se validan pruebas y comportamiento
- Finalizado: cuando la evidencia queda aprobada

---

## Comentario para Jira
Se recomienda dejar en la descripción de cada tarea este tipo de texto:

> Se implementó la funcionalidad de planificación de rutas con validación de origen y destino, recomendación basada en directos y transbordos, y renderizado del recorrido en el mapa. La evidencia técnica se encuentra en los archivos indicados y se validó con pruebas automatizadas.

---

## Resumen de cierre
Cuando la tarea pasa a Finalizado, el ticket debe quedar con:
- descripción completa
- evidencia técnica
- criterios de aceptación
- pruebas
- comentario de aprobación o revisión

Esto deja una trazabilidad clara para que el equipo pueda revisar el trabajo sin necesidad de volver a preguntar “¿qué se hizo exactamente?”.
