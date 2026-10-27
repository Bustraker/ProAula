# Registro y acceso de administradores

El registro público de Bustraker crea únicamente cuentas `ROLE_USER`. No se
aceptan códigos de administrador ni se puede solicitar acceso ADMIN desde una
página pública.

## Flujo de aprobación

1. Un administrador con sesión iniciada abre **Gestionar usuarios** y envía
   nombre, apellido, correo y motivo de acceso del candidato. La solicitud
   registra también el nombre y usuario del administrador que lo recomienda.
   No se piden contraseñas antes de la aprobación.
2. El sistema guarda la solicitud y envía al aprobador el nombre del candidato,
   el nombre de quien recomienda, el correo y el motivo. El mensaje enlaza a la
   solicitud dentro del panel; las acciones de aprobar y rechazar requieren una
   sesión autorizada y protección CSRF. Si Firebase Push está disponible,
   también notifica a los dispositivos registrados.
3. Solo una cuenta con rol `ADMIN` y correo coincidente con
   `ADMIN_APPROVER_EMAIL` puede abrir la bandeja y aprobar o rechazar la
   solicitud. El push y el correo son avisos; nunca aprueban por sí mismos.
4. Al aprobar, el sistema genera un código personal y guarda únicamente su hash
   SHA-256, vinculado al correo y a la solicitud. El código se muestra al
   aprobador después de guardar la aprobación; el aprobador decide explícitamente
   si lo envía al correo del candidato. La bandeja registra quién y cuándo
   solicitó el envío. Si el código se pierde, se puede generar otro; el anterior
   queda invalidado.
5. El candidato abre `/admin-login`, verifica el código y crea su usuario y
   contraseña. La contraseña se almacena mediante el codificador BCrypt de
   Spring Security. El código permanece vinculado a la solicitud y al correo;
   se exige junto con las credenciales en cada acceso ADMIN, de acuerdo con el
   flujo actual.

El remitente SMTP y la cuenta aprobadora son roles separados:
`MAIL_USERNAME` es quien envía; `ADMIN_APPROVER_EMAIL` recibe el aviso y debe
coincidir con el correo de la cuenta ADMIN autorizada. El remitente puede ser
distinto del aprobador. El servidor no almacena el código de acceso en texto
plano. Los enlaces de correo y las notificaciones push solo abren el panel
protegido; aprobar o rechazar nunca se ejecuta al abrir un enlace.

## Configuración local

El perfil `local` desactiva Flyway y usa `ddl-auto: update`. Para probar correo,
configura las variables en el entorno local, sin guardarlas en Git. En Windows
se pueden guardar como variables de entorno del usuario; reinicia VS Code y la
aplicación para que las nuevas sesiones las reciban:

| Variable | Uso |
| --- | --- |
| `SPRING_DATASOURCE_URL` | URL JDBC de la base MySQL local o de desarrollo. |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Credenciales de base de datos. |
| `JWT_SECRET` | Secreto aleatorio para firmar tokens; obligatorio también en local. |
| `JWT_EXPIRATION_MS` | Duración del token en milisegundos; por defecto 86400000. |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | Credenciales OAuth de GitHub, si se habilita ese proveedor. |
| `DISCORD_CLIENT_ID` / `DISCORD_CLIENT_SECRET` | Credenciales OAuth de Discord, si se habilita ese proveedor. |
| `ADMIN_APPROVER_EMAIL` | Correo de la cuenta ADMIN que puede aprobar; debe coincidir con el correo de su usuario. |
| `MAIL_USERNAME` | Cuenta SMTP remitente, con credenciales SMTP válidas. Puede ser diferente del aprobador. |
| `MAIL_PASSWORD` | Contraseña de aplicación SMTP, nunca la contraseña normal de Gmail. |
| `MAIL_HOST` / `MAIL_PORT` | Servidor SMTP; por defecto `smtp.gmail.com` y `587`. |
| `APP_PUBLIC_BASE_URL` | Origen público del servidor Spring; en producción debe ser HTTPS y no incluir una ruta. |
| `SESSION_COOKIE_SECURE` | Déjalo en `false` para desarrollo por HTTP local; en producción debe ser `true`. |

Sin `FIREBASE_ADMIN_ENABLED=true`, credenciales de Firebase Admin y
`FIREBASE_VAPID_KEY`, la aplicación deja push desactivado y el panel lo indica;
el correo al aprobador sigue siendo el canal alternativo.

En el perfil local, `application-local.yml` habilita Firebase Admin y lee el
JSON indicado por `FIREBASE_CREDENTIALS_FILE`, guardado fuera del repositorio
(en Windows, bajo `%LOCALAPPDATA%\ProAula\secrets`). Esa variable guarda la
ruta del archivo, no la clave privada.
La clave pública VAPID de Web Push se configura localmente; en otros entornos
usa `FIREBASE_VAPID_KEY`. La API key del SDK web no sustituye esa clave.

## Despliegue de producción

La aplicación no activa `prod` automáticamente. Configura
`SPRING_PROFILES_ACTIVE=prod` explícitamente y define todas estas variables en
el gestor de secretos del host antes de iniciar. El perfil falla al arrancar si
faltan valores esenciales, en lugar de dejar el flujo aparentemente operativo
con enlaces o correos incompletos.

| Variable | Obligatoria | Uso |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | Sí | URL JDBC MySQL. |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Sí | Credenciales de base de datos. |
| `JWT_SECRET` | Sí | Secreto de firma de al menos 32 caracteres, único y aleatorio. |
| `ADMIN_APPROVER_EMAIL` | Sí | Correo de la cuenta aprobadora ADMIN. |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | Sí | Credenciales de la cuenta SMTP remitente; no tiene que ser el correo aprobador. |
| `APP_PUBLIC_BASE_URL` | Sí | Origen HTTPS público del backend Spring. |
| `FIREBASE_VAPID_KEY` | Sí para push | Clave pública Web Push de Firebase Cloud Messaging. |
| `FIREBASE_ADMIN_ENABLED` | Sí para push | Establecer en `true` para habilitar el envío del backend. |
| `FIREBASE_CREDENTIALS_FILE` | Opcional | Recurso `file:` externo para desarrollo; en Cloud Run se recomienda ADC y dejarlo vacío. |

Los campos de configuración web de Firebase ya tienen valores públicos por
defecto en `application.yml`; no son credenciales del Admin SDK. Restringe la
API key desde Google Cloud Console. Nunca guardes contraseñas, claves privadas
ni el JSON de una cuenta de servicio en el repositorio, `resources` o el
directorio público.

En Cloud Run, concede a la identidad de ejecución únicamente el permiso
necesario para Firebase Cloud Messaging y usa Application Default Credentials.
El valor de `APP_PUBLIC_BASE_URL` debe apuntar al backend real. Firebase Hosting
por sí solo sirve contenido estático: para usarlo como dominio público,
configura un rewrite al backend Spring ya desplegado; no publiques este
repositorio como si fuera una aplicación estática.

Para crear la cuenta aprobadora inicial, usa `BOOTSTRAP_ADMIN_ENABLED`,
`BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_EMAIL` y
`BOOTSTRAP_ADMIN_PASSWORD` en el gestor de secretos, con correo igual a
`ADMIN_APPROVER_EMAIL`. La contraseña inicial debe tener entre 12 y 72 bytes en
UTF-8. Desactiva el aprovisionamiento después de crearla y no uses un registro
público para crear administradores.

## Base de datos y migraciones

Antes del primer despliegue, respalda la base de datos y revisa su esquema y el
contenido de `flyway_schema_history`. El perfil `prod` ejecuta las migraciones
de `src/main/resources/db/migration/mysql` con `ddl-auto: validate`; Flyway no
debe competir con Hibernate para modificar el esquema. No marques una base de
datos existente como vacía ni ejecutes manualmente una migración ya registrada.
Estas migraciones actualizan el flujo ADMIN; no crean desde cero todas las
tablas de la aplicación. La base debe contener previamente el esquema base de
Bustraker.
Si el esquema ya contiene tablas de solicitudes o códigos de una versión
anterior, compara sus columnas e índices con las migraciones antes del
despliegue. Local y las pruebas H2 no validan la compatibilidad con el MySQL de
producción. Si una migración versionada ya se ejecutó en algún entorno, no
edites su archivo ni uses `flyway repair` para ocultar una diferencia de
checksum; crea una nueva migración hacia adelante.

Configura además límites de frecuencia en el proxy/WAF para
`POST /admin/verificar-codigo` y `POST /admin/login`. La aplicación no mantiene
un contador distribuido de intentos y un límite solo en memoria no sería fiable
al escalar a varias instancias.

## Compatibilidad con códigos antiguos

La aplicación ya no crea códigos globales desde `ADMIN_REGISTRATION_CODE`.
Los códigos antiguos que ya estén guardados sin correo asociado se conservan
para no bloquear cuentas existentes; mientras sigan en la base, no tienen la
protección de asociación individual del nuevo flujo. Antes de retirarlos,
identifica y entrega un código personal a cada administrador que dependa de
ellos, verifica su acceso y solo entonces revoca los códigos antiguos. No
borres ni invalides esos registros sin preparar esa transición.

## Qué probar después de configurar el entorno

1. Inicia sesión como aprobador y confirma que el correo de su cuenta es el
   mismo que `ADMIN_APPROVER_EMAIL` (la comparación no distingue mayúsculas).
2. En el dispositivo del aprobador, permite notificaciones y activa push desde
   `/admin/solicitudes-codigo`. El panel solo habilita el control si están
   disponibles tanto VAPID como Firebase Admin.
3. Envía una solicitud de prueba; confirma el correo de aviso y, si push está
   habilitado, que los dispositivos registrados reciben el aviso.
4. Apruébala; confirma que el candidato recibe el código y puede crear la
   cuenta. Rechaza otra solicitud y confirma que no se genera ningún código.
5. Inicia sesión con el candidato aprobado y confirma que el código solo sirve
   con la cuenta ligada a su correo.

Las pruebas automatizadas usan correo y base H2 simulados; no envían correos,
no acceden a Firebase real ni sustituyen la comprobación de las migraciones en
una copia de MySQL de producción.
