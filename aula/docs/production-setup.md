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
## Datos sintéticos de prueba

El perfil local carga las rutas provisionales y sus paradas desde
[`local-reference-data.sql`](../src/main/resources/db/seed/local-reference-data.sql).
En XAMPP, crea primero la base `bustraker` y arranca la aplicación: Hibernate crea
las tablas de entidades, el inicializador SQL añade la referencia que aún no exista
y el seeder Faker local genera datos de demostración en las tablas vacías. La carga
es idempotente. El SQL es la única fuente de rutas y paradas provisionales; el
seeder Java conserva la inicialización de barrios y el aprovisionamiento opcional del
administrador. Usa `SYNTHETIC_DATA_ENABLED=false` para desactivar la generación
sintética.

El seeder crea cuentas `demo_...` identificadas con `provider=SYNTHETIC` y limita
las relaciones sintéticas a esas cuentas. No restaura cuentas, contraseñas, mensajes
ni otros registros personales del respaldo. La exportación privada
`src/bustraker.csv` está excluida de Git; no la agregues al repositorio.

En MySQL local, [`mysql-analytics-schema.sql`](../src/main/resources/db/seed/mysql-analytics-schema.sql)
crea las tablas auxiliares necesarias para esos datos. La aplicación solo permite
la carga sintética MySQL contra `localhost`/`127.0.0.1` y valida el esquema antes de
escribir. Si `demanda_historica.fuente_datos` es un `ENUM` antiguo sin el valor
`SINTETICO`, el bootstrap local lo amplía antes de cargar los datos; las columnas de
texto con espacio suficiente también son compatibles. No habilites esta semilla en
producción.

En el esquema XAMPP `bustraker` usado para esta carga, Hibernate también detecta una
diferencia preexistente: `horario.dias_semana` es `SET` en MySQL pero la entidad Java
espera `VARCHAR`. Solo para esa ejecución local se inició con
`SPRING_JPA_HIBERNATE_DDL_AUTO=none`; esto no modifica el esquema, pero desactiva la
validación de Hibernate durante el proceso. No lo dejes configurado para el uso normal
de la aplicación; corrige esa diferencia mediante una migración revisada antes de
volver a exigir `ddl-auto=validate`.

Las cantidades se pueden acotar con `SYNTHETIC_DATA_USER_COUNT`,
`SYNTHETIC_DATA_BUS_COUNT`, `SYNTHETIC_DATA_TRIP_COUNT`,
`SYNTHETIC_DATA_TICKET_COUNT`, `SYNTHETIC_DATA_HISTORY_DAYS`,
`SYNTHETIC_DATA_POSITION_COUNT` y `SYNTHETIC_DATA_INCIDENT_COUNT`. La semilla
reproducible se configura con `SYNTHETIC_DATA_SEED`. Estos registros sirven para
validar el procesamiento y los pipelines; no representan observaciones reales y no
deben tratarse como evidencia de comportamiento para entrenar o evaluar modelos.


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
