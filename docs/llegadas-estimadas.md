# Llegadas estimadas por reportes voluntarios

## Alcance y honestidad de la estimación

Bustraker no rastrea buses ni recibe horarios de tránsito en tiempo real. La pantalla muestra rangos orientativos calculados a partir de ubicaciones GPS que pasajeros autenticados comparten voluntariamente mientras viajan. El tráfico, los desvíos, una mala señal GPS y las paradas no registradas pueden cambiar el resultado; nunca debe presentarse como una hora garantizada.

## Preparar los datos de las paradas

Las llegadas requieren una ruta activa y verificada, el orden confirmado de sus barrios y coordenadas reales para todas sus paradas activas en cada sentido. Si falta alguna coordenada, el servicio responde `SIN_COORDENADAS` y no interpola ni inventa la ubicación de esa parada.

Un administrador puede abrir **Coordenadas de paradas** desde el editor de rutas o en `/admin/coordenadas-paradas`. Puede ingresar coordenadas verificadas o capturarlas desde el GPS del dispositivo mientras está físicamente en la parada. La captura solo rellena el formulario: la ubicación no se guarda hasta que el administrador elige **Guardar**. No usar el centro del barrio, una ubicación de referencia aproximada ni coordenadas de otra parada.

## Compartir ubicación

1. El pasajero inicia sesión, planifica el viaje y selecciona una ruta.
2. Solo si está a bordo, elige explícitamente **Estoy a bordo · compartir GPS** y permite el GPS del navegador.
3. El navegador envía reportes únicamente con precisión de 65 m o mejor. Cambiar de ruta o usar el control **Detener ubicación compartida** detiene el envío.
4. El servidor elimina los reportes al detenerlos o, como máximo, 90 segundos después de la última actualización. También ejecuta una limpieza periódica.

La lectura de estimaciones es pública. La API solo devuelve el nombre de la ruta y de las paradas, rangos de minutos, confianza, antigüedad y un conteo agregado de reportantes. No devuelve la identidad del pasajero ni las coordenadas GPS que compartió. Publicar y retirar reportes requiere una sesión autenticada y protección CSRF.

## Límites operativos

Los reportes se guardan en memoria y se pierden al reiniciar el proceso; las instancias de una aplicación no comparten sus reportes. Esta implementación es adecuada para una instancia y una primera etapa de validación. Antes de escalar a varias instancias o exigir persistencia, reemplazar el almacenamiento en memoria por un almacén compartido con expiración y mantener el mismo modelo de privacidad.

El cálculo exige muestras recientes con movimiento dentro de rangos de velocidad plausibles. Si todavía no hay movimiento suficiente, responde `SIN_VELOCIDAD`; si el pasajero llegó al final del sentido, responde `SIN_PARADAS_PENDIENTES`. En ambos casos la interfaz muestra el motivo sin inventar una ETA.
