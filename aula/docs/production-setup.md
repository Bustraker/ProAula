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
| `BOOTSTRAP_ADMIN_ENABLED` | No | Solo habilítalo durante el aprovisionamiento inicial de un administrador. |
| `BOOTSTRAP_ADMIN_USERNAME` | Condicional | Usuario inicial; obligatorio si `BOOTSTRAP_ADMIN_ENABLED=true`. |
| `BOOTSTRAP_ADMIN_EMAIL` | Condicional | Correo del administrador inicial; obligatorio si `BOOTSTRAP_ADMIN_ENABLED=true`. |
| `BOOTSTRAP_ADMIN_PASSWORD` | Condicional | Contraseña inicial; obligatorio si `BOOTSTRAP_ADMIN_ENABLED=true`. |
| `MAIL_USERNAME` | Para el flujo de invitaciones ADMIN | Cuenta SMTP emisora; puede ser distinta de `ADMIN_APPROVER_EMAIL`. |
| `MAIL_PASSWORD` | Para el flujo de invitaciones ADMIN | Contraseña de aplicación SMTP, guardada como secreto. |
| `ADMIN_APPROVER_EMAIL` | Para el flujo de invitaciones ADMIN | Correo de la cuenta ADMIN autorizada para aprobar. |
| `APP_PUBLIC_BASE_URL` | No | Origen HTTPS público del backend; por defecto `https://proaula.onrender.com`. |
| `FIREBASE_VAPID_KEY` | Sí para push | Clave pública Web Push configurada en Firebase. |
| `FIREBASE_ADMIN_ENABLED` | Sí para push | Debe ser `true` para enviar notificaciones desde el backend. |
| `FIREBASE_CREDENTIALS_FILE` | Solo si no hay identidad administrada | Ruta `file:` a credenciales externas al repositorio; vacía cuando se usa ADC. |

Flyway debe permanecer habilitado en producción: `application-prod.yml` lo
activa explícitamente para que ejecute las migraciones antes de la validación de
Hibernate. Si el servicio no inicia y el log salta directamente a Hibernate sin
mostrar actividad de Flyway, revisa las variables del servicio en Render y
elimina `SPRING_FLYWAY_ENABLED=false` si está definida; una variable de entorno
puede anular el archivo de perfil.

En desarrollo y producción, configura `ADMIN_APPROVER_EMAIL` mediante el entorno
y asegúrate de que pertenezca a la cuenta ADMIN aprobadora. `MAIL_USERNAME` es
la cuenta SMTP emisora y puede ser diferente del aprobador. Proporciona una
contraseña de aplicación SMTP vigente mediante `MAIL_PASSWORD`; no la guardes
en archivos del proyecto.

El perfil `prod` puede arrancar sin correo ni Firebase configurados. Sin
`MAIL_USERNAME`, `MAIL_PASSWORD` o `ADMIN_APPROVER_EMAIL`, las operaciones de
invitación, aprobación y rechazo ADMIN que requieren correo fallan con un error
explícito de configuración; no se envían mensajes. Para habilitar ese flujo,
configura `ADMIN_APPROVER_EMAIL`, `MAIL_USERNAME` y `MAIL_PASSWORD` en el gestor
de secretos. `APP_PUBLIC_BASE_URL` usa el origen de Render indicado arriba si no
se define. Push permanece deshabilitado de forma predeterminada: configura
`FIREBASE_VAPID_KEY`, `FIREBASE_ADMIN_ENABLED=true` y credenciales Firebase
válidas para habilitarlo. Sin push configurado, la interfaz no permite activarlo.

Genera `JWT_SECRET` fuera del repositorio, por ejemplo con
`openssl rand -base64 48`, y guárdalo en el gestor de secretos del entorno. No uses
un valor predeterminado del proyecto ni lo escribas en archivos versionados; el
perfil local también requiere `JWT_SECRET` como variable de entorno. Define credenciales de
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

Las tablas de favoritos y resúmenes de trayectos requieren aplicar, en este orden,
[`V20261021_01__create_user_favorites.sql`](../src/main/resources/db/migration/mysql/V20261021_01__create_user_favorites.sql)
y [`V20261030_01__create_favorite_trips.sql`](../src/main/resources/db/migration/mysql/V20261030_01__create_favorite_trips.sql)
en la base MySQL/TiDB antes de desplegar esta funcionalidad. Como Flyway sigue
deshabilitado en `application-prod.yml`, esas migraciones no se ejecutarán al arrancar:
respalda la base, confirma que existan `usuario`, `ruta` y `parada`, y aplica cada
script una sola vez. No habilites Flyway ni cambies `ddl-auto` como parte de este
despliegue mientras siga vigente el workaround de conexión.

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

Cuando Flyway esté habilitado, los scripts del perfil MySQL se ejecutan en orden:

1. [`V20261015_01__admin_code_approval_flow.sql`](../src/main/resources/db/migration/mysql/V20261015_01__admin_code_approval_flow.sql)
   crea las tablas del flujo.
2. [`V20261015_02__normalize_horario_days_column.sql`](../src/main/resources/db/migration/mysql/V20261015_02__normalize_horario_days_column.sql)
   normaliza `horario.dias_semana` de MySQL `SET` a `VARCHAR(255)`, conservando
   el valor textual que espera la entidad Java.
3. [`V20261016_01__hash_existing_admin_codes.sql`](../src/main/resources/db/migration/mysql/V20261016_01__hash_existing_admin_codes.sql)
   convierte códigos existentes a hashes.
4. [`V20261017_01__admin_registration_approval.sql`](../src/main/resources/db/migration/mysql/V20261017_01__admin_registration_approval.sql)
   asocia cada código nuevo al correo y a la solicitud aprobada.
5. [`V20261020_01__admin_request_review_and_code_delivery.sql`](../src/main/resources/db/migration/mysql/V20261020_01__admin_request_review_and_code_delivery.sql)
   agrega datos para la revisión de solicitudes ADMIN y la entrega del código.
6. [`V20261021_01__create_user_favorites.sql`](../src/main/resources/db/migration/mysql/V20261021_01__create_user_favorites.sql)
   crea favoritos de usuarios con claves foráneas a rutas y paradas.
7. [`V20261030_01__create_favorite_trips.sql`](../src/main/resources/db/migration/mysql/V20261030_01__create_favorite_trips.sql)
   conserva, por usuario, los resúmenes de trayectos elegidos como favoritos.

Flyway está configurado con `baseline-on-migrate`; antes del primer despliegue
respalda la base y confirma que el esquema base de Bustraker y
`horario.dias_semana` existan. Revisa `flyway_schema_history` y las columnas,
índices y restricciones actuales de las tablas ADMIN. La migración `01` crea
las tablas del flujo y fallará si ya existen sin una migración registrada; la
migración `V20261017_01` sí comprueba columnas, índices y la FK antes de
añadirlos. `ddl-auto=validate` seguirá fallando si los tipos o columnas no
coinciden. El
usuario de conexión necesita permiso para crear tablas e índices y modificar
`horario.dias_semana`. No uses `flyway repair` para ignorar un checksum ni edites
una migración versionada que ya se haya aplicado; crea una migración nueva hacia
adelante después de revisar el esquema.

Actualmente Flyway continúa deshabilitado en el perfil `prod` por el workaround de
Render descrito arriba; la lista indica el orden de los scripts cuando se reactive.
Hasta entonces, aplica manualmente las migraciones requeridas antes del despliegue.

Los perfiles locales y de validación deshabilitan estas migraciones MySQL: el
entorno local administra su esquema con Hibernate y las pruebas usan su esquema H2
aislado. Esto evita ejecutar DDL de producción sobre bases de prueba.

Para copiar los datos de XAMPP a un esquema TiDB ya preparado, consulta la
[guía del migrador TiDB](./tidb-data-migration.md). La copia es explícita, no
destructiva y no sustituye respaldos periódicos ni sincronización continua.

Al terminar Flyway, Hibernate usa `ddl-auto=validate`: comprueba que el esquema
corresponda a las entidades y falla al arrancar si detecta diferencias. No cambies
`ddl-auto` a `update` en producción para sortear errores de esquema; revisa y versiona
cualquier migración adicional antes de desplegarla.

## OAuth

Los proveedores y sus permisos se configuran en `application.yml`, compartido por
los perfiles local y `prod`: Google (`openid profile email`), GitHub
(`user:email`), Discord (`identify email`) y Facebook (`email public_profile`).
Configura las credenciales únicamente en el entorno, nunca en `application*.yml`.

Registra en cada consola la URL exacta de callback que envía la aplicación:
`{BASE_URL}/login/oauth2/code/{registrationId}`. En Render, Facebook usa
`https://proaula.onrender.com/login/oauth2/code/facebook`. Meta exige HTTPS y
descarta el callback local HTTP, por lo que el perfil local puede habilitar HTTPS
con un certificado de desarrollo confiable para `localhost`. En Windows,
ejecuta desde la raíz `aula`:

```powershell
. .\scripts\setup-local-oauth-https.ps1
.\mvnw.cmd spring-boot:run
```

El script genera y confía el certificado solo para el usuario actual, crea una
contraseña aleatoria y la protege con DPAPI para ese usuario, y configura el
perfil local en la terminal actual. También carga en el proceso las credenciales
OAuth guardadas en variables de usuario cuando aún no están disponibles en esa
terminal, sin imprimir sus valores.
Abre la aplicación en `https://localhost:5679`; registra en cada proveedor que se
use en local `https://localhost:5679/login/oauth2/code/{registrationId}`. Para
Facebook, el callback es
`https://localhost:5679/login/oauth2/code/facebook`. Si cambias `PORT`, usa el
mismo puerto al registrar los callbacks. La URL HTTP anterior no es válida para
Facebook Login. `localhost`, `127.0.0.1` y el dominio de Render no son
intercambiables. La prueba `ExternalProviderLoginPageTest` verifica callbacks
locales HTTP y HTTPS y los scopes de autorización.

Los callback exactos por proveedor son:

| Proveedor | Local con HTTPS | Render |
| --- | --- | --- |
| Google | `https://localhost:5679/login/oauth2/code/google` | `https://proaula.onrender.com/login/oauth2/code/google` |
| GitHub | `https://localhost:5679/login/oauth2/code/github` | `https://proaula.onrender.com/login/oauth2/code/github` |
| Discord | `https://localhost:5679/login/oauth2/code/discord` | `https://proaula.onrender.com/login/oauth2/code/discord` |
| Facebook | `https://localhost:5679/login/oauth2/code/facebook` | `https://proaula.onrender.com/login/oauth2/code/facebook` |

Registra solo los proveedores que tengan credenciales configuradas. Render ya
termina HTTPS delante de la aplicación; no habilites un certificado TLS propio
en el contenedor ni cambies sus secretos para resolver callbacks.

### Error de Meta «Invalid Scopes: email»

`email` es un permiso válido de Facebook Login. Meta documenta que los permisos
`email` y `public_profile` no requieren revisión de la aplicación para el uso
normal de Facebook Login ([permisos de Facebook Login](https://developers.facebook.com/documentation/facebook-login/web/)).
Si Meta muestra `Invalid Scopes: email`, rechaza la solicitud antes de volver al
servidor: no es un error de base de datos, sesión ni de la URL de callback, y la
aplicación no puede interceptar ni corregir esa pantalla. Comprueba en el panel
de Meta que `FACEBOOK_CLIENT_ID` identifica la aplicación que estás configurando,
que esa aplicación tiene el caso de uso/producto **Facebook Login** apropiado y
que el ID y secreto pertenecen a la misma aplicación. Si está en modo de
desarrollo, solo sus administradores, desarrolladores, testers y usuarios de
prueba pueden iniciar sesión.

En la app Bustraker comprobada para este despliegue, `email` y `public_profile`
se envían desde local y Render; el `client_id` de Render coincide con el
identificador de la app abierta en Meta, y el caso de uso de Facebook Login está
añadido. La app aparece sin publicar y Meta indica que faltan datos básicos
(icono, URL de política de privacidad, eliminación de datos de usuario y
categoría) para enviarla a revisión. Completa esos requisitos y prueba con una
cuenta incluida en los roles de la app; no publiques la app solo para intentar
ocultar el error de scope. La documentación oficial de Meta indica que `email`
y `public_profile` son permisos válidos de Facebook Login y que no requieren
revisión para el uso normal ([Facebook Login para web](https://developers.facebook.com/documentation/facebook-login/web/)).

Local y Render usan los mismos scopes del repositorio. Si ambos entornos usan el
mismo `FACEBOOK_CLIENT_ID`, una corrección de la configuración de esa aplicación
en Meta aplica a ambos; si usan IDs distintos, revisa cada aplicación por
separado. Tras corregir el permiso/app ID, registra también los callbacks local
y Render indicados arriba. No elimines el scope `email` como arreglo: el flujo
de cuentas necesita el correo del proveedor y rechazará una respuesta que no lo
incluya.

La aplicación solo crea o vincula una cuenta OAuth cuando obtiene un correo:
Google requiere `email_verified=true`; GitHub consulta `/user/emails` y acepta solo
correos verificados; Discord requiere `verified=true` junto con el permiso `email`.
Facebook usa el correo que Graph API devuelve al conceder el permiso `email`; su
respuesta `fields=id,name,email` no entrega una propiedad `email_verified`, por lo que
la aplicación no exige ese atributo inexistente. Facebook no vincula por correo una
cuenta local preexistente sin esa verificación explícita; si el correo ya pertenece a
una cuenta distinta, el inicio se rechaza para evitar tomar control de esa cuenta. Si
Facebook no devuelve correo, el acceso también se rechaza. Los datos opcionales de
nombre se sustituyen por valores neutros para respetar los campos obligatorios de la
cuenta local.

Durante el desarrollo, la pantalla de inicio solo muestra proveedores con ID y secreto
distintos de los valores `placeholder-*`. En Windows, si guardas o cambias variables de
usuario mientras VS Code está abierto, cierra todas las ventanas de VS Code y ábrelo
de nuevo para que la aplicación herede el entorno actualizado. El URI de callback
registrado en cada proveedor debe coincidir exactamente con la URL que envía el
navegador, incluido `localhost` frente a `127.0.0.1`, puerto y ruta.
