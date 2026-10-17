# Configuración para producción

## Perfil y base de datos

Inicia la aplicación con `SPRING_PROFILES_ACTIVE=prod`. El perfil `prod` usa MySQL.
En el workaround temporal actualmente desplegado, Hibernate no valida ni modifica
el esquema (`spring.jpa.hibernate.ddl-auto=none`).

Define estas variables antes de iniciar el proceso:

| Variable | Obligatoria | Uso |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Sí | Debe ser `prod`. |
| `SPRING_DATASOURCE_URL` | Sí | URL JDBC de la base MySQL. |
| `SPRING_DATASOURCE_USERNAME` | Sí | Usuario de base de datos con privilegios mínimos para crear tablas e índices y alterar el tipo de `horario.dias_semana` durante la migración. |
| `SPRING_DATASOURCE_PASSWORD` | Sí | Contraseña de ese usuario. |
| `JWT_SECRET` | Sí | Secreto aleatorio de al menos 32 bytes para firmar JWT. |
| `PORT` | No | Puerto HTTP; por defecto `5679`. |
| `SESSION_COOKIE_SECURE` | No | En producción debe permanecer habilitada. |
| `SESSION_COOKIE_DOMAIN` | No | Dominio de la cookie de sesión, si aplica. |
| `CARTO_API_KEY` | No | Clave de CARTO, si la integración se utiliza. |
| `ADMIN_REGISTRATION_CODE` | No | Código para el flujo de registro administrativo, si está habilitado. |
| `BOOTSTRAP_ADMIN_ENABLED` | No | Solo habilítalo durante el aprovisionamiento inicial de un administrador. |
| `BOOTSTRAP_ADMIN_USERNAME` | Condicional | Usuario inicial; obligatorio si `BOOTSTRAP_ADMIN_ENABLED=true`. |
| `BOOTSTRAP_ADMIN_EMAIL` | Condicional | Correo del administrador inicial; obligatorio si `BOOTSTRAP_ADMIN_ENABLED=true`. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Condicional | Contraseña inicial; obligatorio si `BOOTSTRAP_ADMIN_ENABLED=true`. |
| `MAIL_USERNAME` | Para invitaciones ADMIN | Cuenta Gmail emisora; debe coincidir con `ADMIN_APPROVER_EMAIL`. |
| `MAIL_PASSWORD` | Para invitaciones ADMIN | Contraseña de aplicación de Gmail, guardada como secreto. |
| `ADMIN_APPROVER_EMAIL` | Para invitaciones ADMIN | Correo de la cuenta emisora y de la cuenta ADMIN autorizada para aprobar. |
| `APP_PUBLIC_BASE_URL` | Para enlaces del flujo | URL HTTPS pública del backend, sin barra final. |
| `FIREBASE_VAPID_KEY` | Para notificaciones web | Clave pública Web Push configurada en Firebase. |
| `FIREBASE_ADMIN_ENABLED` | Para enviar notificaciones push | `true` solo cuando Firebase Admin y su identidad/credenciales estén configurados. |
| `FIREBASE_CREDENTIALS_FILE` | Solo si no hay identidad administrada | Ruta `file:` a credenciales externas al repositorio; vacía cuando se usa ADC. |

Genera `JWT_SECRET` fuera del repositorio, por ejemplo con
`openssl rand -base64 48`, y guárdalo en el gestor de secretos del entorno. No uses
el secreto local ni lo escribas en archivos versionados. Define credenciales de
OAuth en el gestor de secretos solo para los proveedores que se vayan a ofrecer:
`GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`,
`GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET`,
`DISCORD_CLIENT_ID`/`DISCORD_CLIENT_SECRET` y
`FACEBOOK_CLIENT_ID`/`FACEBOOK_CLIENT_SECRET`.

## Esquema y despliegue

**Workaround temporal de Render:** el perfil `prod` tiene Flyway deshabilitado y
`ddl-auto=none` porque la conexión TiDB se cierra durante el baseline de Flyway. Esto
permite que Hibernate no rechace el tipo existente de `horario.dias_semana`, pero no
modifica esa columna y deja de validar todo el esquema. No lo consideres una reparación
permanente.

La migración preparada
`src/main/resources/db/migration/mysql/V20261015_02__normalize_horario_days_column.sql`
convierte `horario.dias_semana` de `SET` a `VARCHAR(255)`. Cuando se confirme que TiDB
acepta la operación, habilita Flyway en `application-prod.yml`, ejecuta la migración y
restablece `ddl-auto=validate`. No cambies `ddl-auto` a `update` para ocultar diferencias
de esquema.
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

## Esquema y despliegue

El perfil `prod` ejecuta la migración Flyway
[`V20261015_01__admin_code_approval_flow.sql`](../src/main/resources/db/migration/mysql/V20261015_01__admin_code_approval_flow.sql)
para crear las tablas de solicitudes, invitaciones y suscripciones push, y la
migración
[`V20261015_02__normalize_horario_days_column.sql`](../src/main/resources/db/migration/mysql/V20261015_02__normalize_horario_days_column.sql)
normaliza `horario.dias_semana` de MySQL `SET` a `VARCHAR(255)`, como espera la
entidad Java. La conversión conserva el valor textual de los días seleccionados.
Flyway está configurado con `baseline-on-migrate`; antes del primer despliegue
respalda la base, confirma que las tablas de la migración `01` aún no existan y que
la columna `horario.dias_semana` sí exista. El usuario de conexión necesita permiso
para crear tablas e índices y modificar esa columna. Si parte del esquema ya fue
migrado manualmente, revisa el historial de Flyway antes de ejecutar para evitar
migraciones duplicadas.

Los perfiles locales y de validación deshabilitan estas migraciones MySQL: el
entorno local administra su esquema con Hibernate y las pruebas usan su esquema H2
aislado. Esto evita ejecutar DDL de producción sobre bases de prueba.

Al terminar Flyway, Hibernate usa `ddl-auto=validate`: comprueba que el esquema
corresponda a las entidades y falla al arrancar si detecta diferencias. No cambies
`ddl-auto` a `update` en producción para sortear errores de esquema; revisa y versiona
cualquier migración adicional antes de desplegarla.

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
