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
Abre `http://localhost:3465`.

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

Para habilitar un proveedor OAuth, copia `.env.example` como `.env` dentro de `aula`
y completa únicamente las credenciales necesarias. El archivo se carga en el perfil
`local` y está excluido de Git. Registra en el proveedor la URL de callback local:
`http://localhost:3465/login/oauth2/code/github` (sustituye `github` por el proveedor
configurado).
Sin credenciales válidas, esos botones no aparecen en el formulario de acceso.

Para la inicialización local y los datos sintéticos, consulta
[`production-setup.md`](production-setup.md). El perfil de producción y Render no
dependen de `DB_NAME` ni se modifican con este ajuste.
