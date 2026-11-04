# Desarrollo local

## Arranque local con XAMPP

Requisitos: Git, JDK 21 y MySQL iniciado desde XAMPP. Crea la base de datos en
phpMyAdmin y, desde la carpeta `aula`, ejecuta:

```cmd
run-local.cmd
```

El perfil `local` conecta a `localhost:3306` como `root` sin contraseña y usa la base
`bustraker` de forma predeterminada. Para usar otro nombre, define `DB_NAME` con ese
nombre en `.env` o antes de ejecutar el script. Por ejemplo, cambia `DB_NAME` en
`.env` al nombre que creaste en phpMyAdmin. Para usar `.env`, cópialo primero desde
`.env.example`; está excluido de Git. La aplicación conserva MySQL; no usa una base
H2.
Abre `http://localhost:5679`.

Si `JWT_SECRET` no está definido, el perfil local genera una clave aleatoria al
iniciar. Los tokens de acceso dejan de ser válidos al reiniciar la aplicación. Si
necesitas conservarlos entre reinicios, define `JWT_SECRET` con un valor aleatorio
de al menos 32 bytes; producción siempre requiere que se configure explícitamente.

Firebase Admin y las notificaciones push están deshabilitados por defecto en local.
Para habilitarlos, configura `FIREBASE_ADMIN_ENABLED=true` y proporciona credenciales
de servidor válidas mediante `FIREBASE_CREDENTIALS_FILE` o Application Default
Credentials; sin ellas, el arranque de Firebase falla.

## Reporte de incidentes

Los usuarios autenticados pueden abrir **Reportar incidente** desde el panel. El
formulario requiere tipo, ruta y descripción; al guardar registra el usuario y la
fecha y hora del reporte. La API autenticada para integraciones acepta
`POST /api/incidentes` con un cuerpo como:

```json
{
  "tipo": "RETRASO",
  "rutaId": 1,
  "descripcion": "El bus presenta un retraso en el recorrido."
}
```

Los tipos admitidos son `RETRASO`, `ACCIDENTE`, `FALLA_SERVICIO`,
`RUTA_DESVIADA` y `OTRO`. El proyecto reutiliza la tabla `incidente` que ya se
prepara en el esquema de datos local.

El panel autenticado está disponible en `/incidentes/panel` y consulta
`GET /api/incidentes`. Se puede filtrar con `tipo` y `ruta`; este último busca
por nombre o código de ruta, placa o número interno del bus. Por ejemplo:
`GET /api/incidentes?tipo=RETRASO&ruta=RUT-1&page=0&size=20`. La respuesta
incluye `content`, `page`, `size`, `totalElements`, `totalPages`, `first` y
`last`; el tamaño debe estar entre 1 y 100. El panel permite recorrer páginas
y actualizar el estado de cada reporte sin abandonar la vista.

Solo los administradores pueden cambiar el estado con
`PATCH /api/incidentes/{id}/estado`, usando uno de los valores
`PENDIENTE`, `EN_ATENCION`, `RESUELTO` o `DESCARTADO` en el cuerpo:

```json
{
  "estado": "EN_ATENCION"
}
```

La API devuelve el incidente actualizado; responde con `400` ante un estado o
parámetro no válido y `404` cuando el incidente no existe.

Para habilitar un proveedor OAuth, copia `.env.example` como `.env` dentro de `aula`
y completa únicamente las credenciales necesarias. El archivo se carga en el perfil
`local` y está excluido de Git. La URI de callback se fija con
`APP_PUBLIC_BASE_URL`, para que no cambie según el esquema detectado en cada
petición. Si arrancas con `run-local.cmd`, su valor predeterminado es
`http://localhost:5679`; registra en Google Cloud
`http://localhost:5679/login/oauth2/code/google`.

Si arrancas con HTTPS usando
[`setup-local-oauth-https.ps1`](../scripts/setup-local-oauth-https.ps1), abre
`https://localhost:5679` y registra
`https://localhost:5679/login/oauth2/code/google`. Para Facebook, siempre inicia
con HTTPS y registra `https://localhost:5679/login/oauth2/code/facebook`. La URI
registrada debe coincidir exactamente con `APP_PUBLIC_BASE_URL`, incluyendo
`http`/`https`, `localhost` (no `127.0.0.1`) y el puerto; usa el mismo host para
abrir la aplicación y recibir el callback. Puedes registrar ambas URI de Google si
alternas entre HTTP y HTTPS. No es necesario añadir el origen a **Authorized
JavaScript origins** para este flujo OAuth del servidor. Sin
credenciales válidas, esos botones no aparecen en el formulario de acceso.

Para la inicialización local y los datos sintéticos, consulta
[`production-setup.md`](production-setup.md). El perfil de producción y Render no
dependen de `DB_NAME` ni se modifican con este ajuste.
