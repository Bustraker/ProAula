# Configuración para producción

## Perfil y base de datos

Inicia la aplicación con `SPRING_PROFILES_ACTIVE=prod`. El perfil `prod` usa MySQL y
`spring.jpa.hibernate.ddl-auto=validate`: comprueba que las tablas y columnas ya
existen y son compatibles, pero no crea ni modifica el esquema.

Define estas variables antes de iniciar el proceso:

| Variable | Obligatoria | Uso |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Sí | Debe ser `prod`. |
| `SPRING_DATASOURCE_URL` | Sí | URL JDBC de la base MySQL. |
| `SPRING_DATASOURCE_USERNAME` | Sí | Usuario de base de datos con privilegios mínimos. |
| `SPRING_DATASOURCE_PASSWORD` | Sí | Contraseña de ese usuario. |
| `JWT_SECRET` | Sí | Secreto aleatorio de al menos 32 bytes para firmar JWT. |
| `PORT` | No | Puerto HTTP; por defecto `3465`. |
| `SESSION_COOKIE_SECURE` | No | En producción debe permanecer habilitada. |
| `SESSION_COOKIE_DOMAIN` | No | Dominio de la cookie de sesión, si aplica. |
| `CARTO_API_KEY` | No | Clave de CARTO, si la integración se utiliza. |
| `ADMIN_REGISTRATION_CODE` | No | Código para el flujo de registro administrativo, si está habilitado. |
| `BOOTSTRAP_ADMIN_ENABLED` | No | Solo habilítalo durante el aprovisionamiento inicial de un administrador. |
| `BOOTSTRAP_ADMIN_USERNAME` | Condicional | Usuario inicial; obligatorio si `BOOTSTRAP_ADMIN_ENABLED=true`. |
| `BOOTSTRAP_ADMIN_EMAIL` | Condicional | Correo del administrador inicial; obligatorio si `BOOTSTRAP_ADMIN_ENABLED=true`. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Condicional | Contraseña inicial; obligatorio si `BOOTSTRAP_ADMIN_ENABLED=true`. |

Genera `JWT_SECRET` fuera del repositorio, por ejemplo con
`openssl rand -base64 48`, y guárdalo en el gestor de secretos del entorno. No uses
el secreto local ni lo escribas en archivos versionados. Define credenciales de
OAuth en el gestor de secretos solo para los proveedores que se vayan a ofrecer:
`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`,
`GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET`,
`DISCORD_CLIENT_ID`/`DISCORD_CLIENT_SECRET` y
`FACEBOOK_CLIENT_ID`/`FACEBOOK_CLIENT_SECRET`.

## Esquema y despliegue

No hay una migración Flyway/Liquibase versionada en este proyecto. Antes de desplegar,
aprovisiona o migra la base por un proceso controlado y confirma que su esquema
corresponda a las entidades actuales. La aplicación fallará al arrancar si `validate`
detecta diferencias. No cambies `ddl-auto` a `update` en producción para sortear
errores de esquema; define y revisa las migraciones antes de automatizar cambios.

## OAuth

Registra en cada proveedor la URL de callback
`{BASE_URL}/login/oauth2/code/{registrationId}` (por ejemplo,
`https://app.example.com/login/oauth2/code/google`). Configura las credenciales
únicamente en el entorno, nunca en `application*.yml`.

La aplicación solo crea o vincula una cuenta OAuth cuando puede obtener un correo
que considera verificado. Google requiere `email_verified=true`; GitHub consulta
`/user/emails` y acepta solo correos verificados; Discord requiere `verified=true`.
Facebook actualmente también requiere `email_verified=true`. Comprueba con una
cuenta de prueba y la respuesta real de la aplicación de Facebook que dicho atributo
se entregue; si no está disponible, ese flujo se rechazará de forma segura y no se
debe relajar la verificación ni vincular por correo sin diseñar un flujo explícito
de verificación/confirmación.
