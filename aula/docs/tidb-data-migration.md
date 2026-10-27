# Migración de datos de XAMPP/MySQL a TiDB Cloud

`scripts/migrate_mysql_to_tidb.py` copia una sola vez las filas de la base local
`bustraker` a un esquema TiDB existente. No crea el esquema, no elimina datos y
no es una sincronización ni un respaldo automático. Las tablas del destino deben
existir, tener columnas compatibles y estar vacías; el script aborta si detecta
tablas o columnas faltantes, incompatibilidades o filas existentes. Si una tabla
ya se copió, no vuelvas a ejecutar la migración sobre ella.

La aplicación local sigue usando XAMPP mediante `SPRING_DATASOURCE_URL` en
`application-local.yml`. La conexión de destino del migrador usa variables
separadas (`TARGET_DB_*`), para no reemplazar ni confundir la configuración local
con las variables de despliegue de Render.

## Preparación

1. Haz y verifica un respaldo independiente de XAMPP y de TiDB.
2. Prepara en TiDB el esquema completo compatible con la aplicación antes de
   copiar datos. Las migraciones Flyway de producción actualizan partes del
   esquema base; no crean desde cero todas las tablas de Bustraker.
3. Instala Python 3.10 o posterior y las dependencias del migrador desde la raíz
   de `aula`:

   ```powershell
   python -m pip install -r scripts\requirements-tidb-migration.txt
   ```

## Conexión y simulación

Desde PowerShell, en la raíz de `aula`, indica el host y usuario del clúster
TiDB. También puedes omitirlos y responder a las preguntas del programa:

```powershell
$env:TARGET_DB_HOST = Read-Host "Host de TiDB Cloud"
$env:TARGET_DB_USER = Read-Host "Usuario de TiDB Cloud"
python scripts\migrate_mysql_to_tidb.py
```

El origen usa XAMPP (`127.0.0.1:3306`, base `bustraker`, usuario `root`) por
defecto. Si la contraseña de XAMPP o TiDB no está en `SOURCE_DB_PASSWORD` o
`TARGET_DB_PASSWORD`, el programa la solicita sin mostrarla mientras se escribe.
El certificado CA incluido se usa para verificar TLS de TiDB; se puede indicar
otro archivo con `TARGET_DB_SSL_CA`.

La ejecución anterior es solo una simulación: conecta a ambas bases, compara
tablas y columnas, verifica que el destino esté vacío y muestra los conteos; no
inserta filas. Revisa toda la salida antes de autorizar la escritura.

## Copia explícita

Solo después de verificar la simulación y confirmar que el esquema y destino son
correctos, ejecuta el comando de escritura confirmando exactamente el nombre de
la base TiDB:

```powershell
python scripts\migrate_mysql_to_tidb.py --execute --confirm-target test
```

Sustituye `test` si el destino tiene otro nombre. La opción `--execute` inserta
filas en TiDB por bloques y verifica los conteos; las tablas se copian en orden
de dependencias. No uses la contraseña como argumento de consola ni la guardes
en Git o en el proyecto. `migracion_tidb.log` se escribe en el directorio desde
el que ejecutas el script y está excluido de Git.

Una migración completa **no mantiene un respaldo continuo**: para respaldos
periódicos usa exportaciones versionadas/fechadas y verificadas, o un proceso de
sincronización dedicado. Este script rechaza tablas destino con filas para evitar
duplicados y no implementa actualizaciones incrementales.
