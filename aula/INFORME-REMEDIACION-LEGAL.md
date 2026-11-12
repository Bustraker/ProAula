# Informe de remediación técnica legal y de privacidad

**Proyecto:** Bustraker
**Fecha:** 11 de noviembre de 2026
**Versión legal registrada por la aplicación:** `2026-11-11-v1`

> Este informe documenta cambios técnicos y brechas observadas en el repositorio. No es un dictamen jurídico. La identidad del responsable, los contratos de proveedores y las decisiones de conservación requieren confirmación del titular del servicio y revisión profesional antes de publicar.

## Resultado ejecutivo

Se implementaron controles de autorización expresa y mayoría de edad en los registros web, API, administrativos y de acceso social. La autorización se conserva con usuario, fecha, versiones de los documentos, confirmación de edad y canal de aceptación. Las rutas protegidas de la aplicación exigen consentimiento vigente; los datos de consulta pública siguen accesibles. Los reportes GPS en vivo requieren consentimiento vigente.

También se añadió la revocación desde el perfil: conserva la evidencia con su fecha de revocación, cierra la sesión y elimina del navegador las cookies de sesión y JWT. Se actualizaron las páginas independientes de Términos y Política de privacidad para explicar este flujo, además de la información sobre GPS, modelos predictivos, proveedores identificables en el código, derechos y plazos de respuesta, conservación conocida y créditos de mapas.

**Bustraker no debe considerarse listo para publicación pública todavía.** No se inventaron datos que el repositorio no permite verificar. La identidad legal del responsable, los plazos definitivos de conservación y varios datos de proveedores siguen sin estar disponibles; además, la migración requiere aplicación en la base de producción.

## Cambios realizados

### Consentimiento y edad

- Se añadió la entidad `legal_consent`, su repositorio y un servicio de registro y verificación.
- La evidencia conserva el usuario, la fecha/hora del consentimiento, versiones de Términos y Privacidad, confirmación de 18 años y canal de aceptación. En OAuth, la hora se captura antes de salir al proveedor y se persiste al completar el acceso.
- El registro web, el endpoint de registro de usuarios y el registro administrativo exigen aceptación afirmativa y confirmación de mayoría de edad. El registro API también valida la versión legal.
- Los botones de acceso social ahora se envían mediante un formulario con aceptación y edad. Un filtro impide iniciar OAuth directamente sin esa autorización; tras autenticarse, se guarda la evidencia.
- Se agregó una página de reaceptación para cuentas existentes y un aviso en el perfil cuando no existe aceptación vigente.
- Un filtro global bloquea páginas privadas y escrituras API de cuentas autenticadas sin consentimiento vigente. Mantiene disponibles únicamente las páginas legales, autenticación, recursos estáticos y lecturas públicas verificadas.
- El perfil permite retirar la autorización mediante POST protegido por CSRF. La revocación conserva el registro original y marca `revoked_at`; se expiran la cookie JWT y la sesión del navegador.
- El endpoint de reporte de ubicación en vivo rechaza el envío si la cuenta no tiene consentimiento vigente. La interfaz comunica cómo completar la confirmación.
- El alta API y el alta administrativa guardan usuario y aceptación dentro de la misma transacción para evitar crear una cuenta sin su evidencia si falla la escritura legal.

### Base de datos

- Migración MySQL: `src/main/resources/db/migration/mysql/V20261111_01__create_legal_consent.sql`.
- Migración MySQL adicional: `src/main/resources/db/migration/mysql/V20261111_02__add_legal_consent_revocation.sql`.
- El esquema de validación de pruebas incorpora la tabla y la marca de revocación.
- En producción `ddl-auto` está deshabilitado y Flyway no se ejecuta automáticamente con la configuración actual. Ambas migraciones deben aplicarse en orden y verificarse en producción antes de habilitar los nuevos registros o desplegar esta versión.

### Documentos e información del producto

- Se conservaron las páginas separadas de [Términos y condiciones](./src/main/resources/templates/terminos.html) y [Política de privacidad](./src/main/resources/templates/privacidad.html).
- Los documentos describen el consentimiento, la regla de edad de 18 años, los derechos y plazos generales de consultas y reclamos, los datos de GPS y su vencimiento en memoria a los 90 segundos.
- Se describen los campos enviados al servicio de modelos predictivos y se aclara que el endpoint de modelos puede configurarse; no se afirma que un proveedor externo esté impedido de usar los datos para entrenamiento.
- Se identifican las cargas de cdnjs/Font Awesome y los servicios de mapas y enrutamiento visibles en el código. Los mapas mantienen las atribuciones disponibles en la interfaz.
- El resultado de riesgo se identifica visualmente como generado por un modelo predictivo.
- Los documentos no contienen avisos dirigidos a desarrolladores del tipo “pendiente de completar” o “debe confirmar”; sí mantienen información factual sobre aquello que no se pudo verificar, sin presentar como cierto un dato inexistente.

## Verificación realizada

- Pruebas Maven focalizadas de cuenta/consentimiento: **14 pruebas, 0 fallas, 0 errores**. Incluyen reaceptación, bloqueo de páginas y escrituras API, renderizado del control de revocación, persistencia de la revocación y cierre de sesión.
- Suite Maven completa: **178 pruebas, 177 pasaron y 1 falló**. La única falla es una aserción preexistente de `ConsultasPageIntegrationTest` que espera versiones antiguas de recursos estáticos (`green-13` y `route-map-2`), mientras la plantilla vigente usa `cartagena-contacto-15` y `route-map-3`; no está relacionada con los cambios legales.
- Pruebas Playwright de páginas legales, ejecutadas antes de esta última fase: **2 pasaron**.
- `git diff --check`: pasó sin errores.

## Pendientes que bloquean la publicación

### 1. Identificación del responsable

El repositorio no proporciona nombre o razón social, CC/NIT, domicilio, correo y teléfono del responsable, ni una persona o área que atienda consultas y reclamos. La marca “Bustraker” no sustituye esa identificación y el formulario de contacto no la reemplaza. No se inventaron estos datos. Deben incorporarse a la Política de privacidad y configurarse en el canal de atención antes de publicar.

### 2. Proveedores, países y contratos

El código permite confirmar algunos servicios (proveedores sociales configurables, CARTO, Esri, rutas OSRM, cdnjs y un servicio de modelos configurable), pero no determina qué proveedores están activos en producción, el hosting, la base de datos, el correo ni el país concreto de tratamiento. Tampoco se verificaron los contratos, la retención del proveedor de modelos ni el uso de datos para entrenar modelos. La autorización y la política no sustituyen esa verificación contractual.

### 3. Conservación y eliminación

El código establece 90 segundos para los reportes GPS activos, pero no define eliminación automática para cuentas, incidentes o mensajes de contacto. La tabla de la Política de privacidad refleja esta situación; no inventa plazos. El responsable debe aprobar periodos por categoría y, después, implementarlos con tareas de eliminación o anonimización y pruebas. El cierre de cuenta puede solicitarse por contacto, pero no existe un flujo técnico de autoservicio que cierre la cuenta y procese integralmente sus datos.

### 4. Logs e infraestructura del GPS

El servicio de llegadas mantiene los reportes en memoria y los elimina al vencer o detenerse. La revisión del código no encontró que ese servicio escriba coordenadas en sus propios logs. No se verificaron los logs del servidor, proxy inverso, hosting, monitoreo o respaldos de la instalación; por eso la política no promete que esos sistemas carezcan de registros. Deben configurarse y auditarse antes del despliegue.

### 5. Mapas y enrutamiento

El código consulta servidores públicos de OSRM como servicio de rutas y puede enviarles coordenadas de origen/destino. Los servidores demo no ofrecen garantías y tienen restricciones de uso; no son una base apropiada para asumir producción o uso comercial. Debe confirmarse su política vigente y reemplazarse por una instancia propia o proveedor contratado antes de ese escenario.

La configuración de CARTO usa `CARTO_API_KEY`, cuyo valor predeterminado está vacío. Se debe validar la clave, atribución, términos de uso y propósito comercial de la cuenta desplegada. También se deben confirmar las políticas actuales de Esri y OpenStreetMap según las capas efectivamente utilizadas.

### 6. Incidentes de seguridad y derechos

La Política describe la notificación a la SIC y a las personas afectadas cuando la normativa lo exija. No se encontró un flujo técnico de respuesta, registro, evaluación y notificación de incidentes de privacidad. El canal de contacto tampoco constituye por sí solo un sistema de gestión con responsables y vencimientos verificables.

### 7. Alcance de la mayoría de edad

La edad se confirma mediante declaración expresa; no existe verificación documental. El servidor bloquea los reportes GPS sin esa confirmación vigente, pero el control no equivale a comprobar la edad real ni a una evaluación jurídica sobre el tratamiento de datos de menores.

Las cuentas antiguas sin una aceptación vigente reciben una invitación a reaceptar en el perfil y no pueden compartir GPS hasta hacerlo. El resto de funciones de una cuenta existente no se bloquea globalmente por falta de esa evidencia; si se decide exigir reaceptación para toda la plataforma, debe diseñarse y probarse ese cambio de acceso antes de desplegarlo.

## Lista antes de desplegar

1. Completar y verificar la identidad del responsable y el contacto oficial de privacidad.
2. Identificar proveedores activos, países, términos, contratos y tratamiento de datos para entrenamiento.
3. Definir plazos de conservación por categoría y automatizar su ejecución.
4. Auditar logs, proxies, monitoreo y respaldos respecto de coordenadas GPS.
5. Resolver el uso productivo de OSRM y verificar CARTO, Esri y OpenStreetMap.
6. Aplicar en orden ambas migraciones MySQL (`V20261111_01` y `V20261111_02`) y comprobar la existencia de `legal_consent.revoked_at` antes del despliegue.
7. Actualizar o corregir, en una tarea aparte, la aserción obsoleta de versiones de recursos en `ConsultasPageIntegrationTest` y volver a ejecutar la suite completa.
8. Hacer revisión jurídica local de los documentos y del flujo para menores.
