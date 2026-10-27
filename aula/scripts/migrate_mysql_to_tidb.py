"""Copy rows from the local Bustraker MySQL database to an existing TiDB schema.

The script is read-only by default. Set TARGET_DB_HOST and TARGET_DB_USER,
review the dry run, then pass --execute --confirm-target <target database>.
Passwords are read from the environment or prompted without echoing.
"""

from __future__ import annotations

import argparse
import getpass
import heapq
import logging
import os
import sys
from dataclasses import dataclass
from typing import Any

LOGGER = logging.getLogger("mysql_to_tidb")
DEFAULT_SOURCE_DATABASE = "bustraker"
DEFAULT_TARGET_DATABASE = "test"
DEFAULT_BATCH_SIZE = 500


@dataclass(frozen=True)
class DatabaseConfig:
    host: str
    port: int
    database: str
    username: str
    password: str
    ssl_ca: str | None = None


def quote_identifier(identifier: str) -> str:
    """Quote a MySQL identifier so table and column names cannot alter SQL."""
    if not identifier or "\x00" in identifier:
        raise ValueError("Se encontró un nombre de tabla o columna no válido.")
    return f"`{identifier.replace('`', '``')}`"


def order_tables_by_dependencies(
    tables: list[str], foreign_keys: list[tuple[str, str]]
) -> list[str]:
    """Order tables parent-first; stop before copying if dependencies are cyclic."""
    table_set = set(tables)
    children: dict[str, set[str]] = {table: set() for table in tables}
    in_degree = {table: 0 for table in tables}

    for child, parent in foreign_keys:
        if child not in table_set or parent not in table_set:
            continue
        if child not in children[parent]:
            children[parent].add(child)
            in_degree[child] += 1

    ready = [table for table, degree in in_degree.items() if degree == 0]
    heapq.heapify(ready)
    ordered: list[str] = []

    while ready:
        parent = heapq.heappop(ready)
        ordered.append(parent)
        for child in sorted(children[parent]):
            in_degree[child] -= 1
            if in_degree[child] == 0:
                heapq.heappush(ready, child)

    if len(ordered) != len(tables):
        cyclic = sorted(table for table, degree in in_degree.items() if degree)
        raise ValueError(
            "Hay claves foráneas circulares que requieren una migración "
            f"manual antes de continuar: {', '.join(cyclic)}"
        )
    return ordered


def _required_target_columns(
    source_columns: dict[str, dict[str, Any]],
    target_columns: dict[str, dict[str, Any]],
) -> list[str]:
    missing: list[str] = []
    for name, column in target_columns.items():
        if name in source_columns or "generated" in str(column["extra"]).lower():
            continue
        has_default = column["column_default"] is not None
        nullable = column["is_nullable"] == "YES"
        auto_increment = "auto_increment" in str(column["extra"]).lower()
        if not (nullable or has_default or auto_increment):
            missing.append(name)
    return missing


def _load_connector() -> Any:
    try:
        import mysql.connector
    except ImportError as exc:
        raise RuntimeError(
            "Falta el conector de MySQL. Instálalo con: "
            "python -m pip install -r scripts/requirements-tidb-migration.txt"
        ) from exc
    return mysql.connector


def _prompt_value(environment_name: str, prompt: str, default: str | None = None) -> str:
    value = os.getenv(environment_name)
    if value:
        return value
    suffix = f" [{default}]" if default else ""
    entered = input(f"{prompt}{suffix}: ").strip()
    return entered or default or ""


def _target_username() -> str:
    return _prompt_value("TARGET_DB_USER", "Usuario TiDB")


def _connection_configs() -> tuple[DatabaseConfig, DatabaseConfig]:
    source = DatabaseConfig(
        host=_prompt_value("SOURCE_DB_HOST", "Host MySQL local", "127.0.0.1"),
        port=int(_prompt_value("SOURCE_DB_PORT", "Puerto MySQL local", "3306")),
        database=_prompt_value(
            "SOURCE_DB_NAME", "Base MySQL local", DEFAULT_SOURCE_DATABASE
        ),
        username=_prompt_value("SOURCE_DB_USER", "Usuario MySQL local", "root"),
        password=os.getenv("SOURCE_DB_PASSWORD")
        or getpass.getpass("Contraseña MySQL local: "),
    )
    target = DatabaseConfig(
        host=_prompt_value(
            "TARGET_DB_HOST",
            "Host de TiDB Cloud",
        ),
        port=int(_prompt_value("TARGET_DB_PORT", "Puerto de TiDB Cloud", "4000")),
        database=_prompt_value(
            "TARGET_DB_NAME", "Base de datos TiDB", DEFAULT_TARGET_DATABASE
        ),
        username=_target_username(),
        password=os.getenv("TARGET_DB_PASSWORD")
        or getpass.getpass("Contraseña TiDB: "),
        ssl_ca=os.getenv("TARGET_DB_SSL_CA")
        or os.path.join(os.path.dirname(__file__), "isrgrootx1.pem"),
    )
    for label, config in (("MySQL local", source), ("TiDB Cloud", target)):
        if not all((config.host, config.database, config.username)):
            raise ValueError(f"Faltan datos de conexión para {label}.")
        if label == "TiDB Cloud" and not config.password:
            raise ValueError("La contraseña de TiDB Cloud no puede estar vacía.")
        if not 1 <= config.port <= 65535:
            raise ValueError(f"El puerto configurado para {label} no es válido.")
    return source, target


def _connect(connector: Any, config: DatabaseConfig, *, use_tls: bool) -> Any:
    options: dict[str, Any] = {
        "host": config.host,
        "port": config.port,
        "database": config.database,
        "user": config.username,
        "password": config.password,
        "connection_timeout": 15,
        "charset": "utf8mb4",
    }
    if use_tls:
        options.update(
            ssl_disabled=False,
            ssl_verify_cert=True,
            ssl_verify_identity=True,
        )
        if config.ssl_ca:
            options["ssl_ca"] = config.ssl_ca
    return connector.connect(**options)


def _fetch_all(connection: Any, query: str, params: tuple[Any, ...] = ()) -> list[Any]:
    cursor = connection.cursor(dictionary=True)
    try:
        cursor.execute(query, params)
        return cursor.fetchall()
    finally:
        cursor.close()


def _get_tables(connection: Any, database: str) -> list[str]:
    rows = _fetch_all(
        connection,
        """
        SELECT table_name AS table_name
        FROM information_schema.tables
        WHERE table_schema = %s AND table_type = 'BASE TABLE'
        ORDER BY table_name
        """,
        (database,),
    )
    return [row["table_name"] for row in rows]


def _get_columns(connection: Any, database: str, table: str) -> dict[str, dict[str, Any]]:
    rows = _fetch_all(
        connection,
        """
        SELECT
            column_name AS column_name,
            is_nullable AS is_nullable,
            column_default AS column_default,
            extra AS extra,
            ordinal_position AS ordinal_position
        FROM information_schema.columns
        WHERE table_schema = %s AND table_name = %s
        ORDER BY ordinal_position
        """,
        (database, table),
    )
    return {row["column_name"]: row for row in rows}


def _get_foreign_keys(connection: Any, database: str) -> list[tuple[str, str]]:
    rows = _fetch_all(
        connection,
        """
        SELECT
            table_name AS table_name,
            referenced_table_name AS referenced_table_name
        FROM information_schema.key_column_usage
        WHERE constraint_schema = %s
          AND referenced_table_name IS NOT NULL
        """,
        (database,),
    )
    return [(row["table_name"], row["referenced_table_name"]) for row in rows]


def _count_rows(connection: Any, table: str) -> int:
    cursor = connection.cursor()
    try:
        cursor.execute(f"SELECT COUNT(*) FROM {quote_identifier(table)}")
        result = cursor.fetchone()
        return int(result[0])
    finally:
        cursor.close()


def _prepare_plan(
    source_connection: Any,
    target_connection: Any,
    source_database: str,
    target_database: str,
) -> list[tuple[str, list[str], int]]:
    source_tables = _get_tables(source_connection, source_database)
    target_tables = set(_get_tables(target_connection, target_database))
    if not source_tables:
        raise ValueError(
            f"No se encontraron tablas en la base local '{source_database}'."
        )

    missing_tables = sorted(set(source_tables) - target_tables)
    if missing_tables:
        raise ValueError(
            "Faltan tablas en TiDB. Primero crea allí el esquema de Bustraker: "
            + ", ".join(missing_tables)
        )

    dependencies = _get_foreign_keys(source_connection, source_database)
    ordered_tables = order_tables_by_dependencies(source_tables, dependencies)
    plan: list[tuple[str, list[str], int]] = []

    for table in ordered_tables:
        source_columns = _get_columns(source_connection, source_database, table)
        target_columns = _get_columns(target_connection, target_database, table)
        missing_source_columns = sorted(
            name
            for name, column in source_columns.items()
            if name not in target_columns
            and "generated" not in str(column["extra"]).lower()
        )
        if missing_source_columns:
            raise ValueError(
                f"TiDB no tiene columnas de la tabla '{table}' que sí existen "
                "en MySQL: "
                + ", ".join(missing_source_columns)
            )
        missing_columns = _required_target_columns(source_columns, target_columns)
        if missing_columns:
            raise ValueError(
                f"La tabla '{table}' en TiDB tiene columnas obligatorias que no "
                "existen en MySQL: "
                + ", ".join(sorted(missing_columns))
            )

        columns = [
            name
            for name in source_columns
            if name in target_columns
            and "generated" not in str(source_columns[name]["extra"]).lower()
            and "generated" not in str(target_columns[name]["extra"]).lower()
        ]
        if not columns:
            raise ValueError(
                f"No hay columnas compatibles para copiar en la tabla '{table}'."
            )

        target_count = _count_rows(target_connection, table)
        if target_count:
            raise ValueError(
                f"La tabla '{table}' en TiDB ya tiene {target_count} registros. "
                "La migración solo copia a tablas vacías para evitar duplicados; "
                "no se borrará ni sobrescribirá ningún dato."
            )
        source_count = _count_rows(source_connection, table)
        plan.append((table, columns, source_count))

    return plan


def _copy_table(
    source_connection: Any,
    target_connection: Any,
    table: str,
    columns: list[str],
    expected_count: int,
    batch_size: int,
) -> int:
    metadata_cursor = source_connection.cursor()
    try:
        metadata_cursor.execute(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = %s
              AND data_type = 'set'
            """,
            (table,),
        )
        set_columns = {row[0] for row in metadata_cursor.fetchall()}
    finally:
        metadata_cursor.close()

    select_columns_sql = ", ".join(
        (
            f"CAST({quote_identifier(column)} AS CHAR) "
            f"AS {quote_identifier(column)}"
            if column in set_columns
            else quote_identifier(column)
        )
        for column in columns
    )
    placeholders = ", ".join(["%s"] * len(columns))
    select_sql = (
        f"SELECT {select_columns_sql} FROM {quote_identifier(table)}"
    )
    insert_columns_sql = ", ".join(quote_identifier(column) for column in columns)
    insert_sql = (
        f"INSERT INTO {quote_identifier(table)} ({insert_columns_sql}) "
        f"VALUES ({placeholders})"
    )

    source_cursor = source_connection.cursor()
    target_cursor = target_connection.cursor()
    copied = 0
    try:
        target_connection.start_transaction()
        source_cursor.execute(select_sql)
        while True:
            rows = source_cursor.fetchmany(batch_size)
            if not rows:
                break
            target_cursor.executemany(insert_sql, rows)
            copied += len(rows)
            LOGGER.info("Tabla %s: bloque copiado; total=%d", table, copied)
        if copied != expected_count:
            raise RuntimeError(
                f"La tabla '{table}' cambió durante la migración: se esperaban "
                f"{expected_count} filas y se copiaron {copied}."
            )
        if _count_rows(target_connection, table) != expected_count:
            raise RuntimeError(
                f"La verificación de filas falló para la tabla '{table}'."
            )
        target_connection.commit()
    except Exception:
        target_connection.rollback()
        raise
    finally:
        source_cursor.close()
        target_cursor.close()
    return copied


def run(*, execute: bool, confirmed_target: str | None, batch_size: int) -> int:
    connector = _load_connector()
    source_config, target_config = _connection_configs()

    if batch_size < 1:
        raise ValueError("--batch-size debe ser mayor que cero.")
    if execute and confirmed_target != target_config.database:
        raise ValueError(
            "Para escribir datos, confirma el destino con "
            f"--confirm-target {target_config.database}"
        )

    source_connection = None
    target_connection = None
    try:
        source_connection = _connect(connector, source_config, use_tls=False)
        target_connection = _connect(connector, target_config, use_tls=True)
        plan = _prepare_plan(
            source_connection,
            target_connection,
            source_config.database,
            target_config.database,
        )
        target_connection.commit()
        total_rows = sum(row_count for _, _, row_count in plan)

        print(f"MySQL local: {source_config.host}/{source_config.database}")
        print(f"TiDB Cloud: {target_config.host}/{target_config.database}")
        print(f"Tablas verificadas: {len(plan)}; registros de origen: {total_rows}")
        for table, _, row_count in plan:
            print(f"  {table}: {row_count} registros")

        if not execute:
            print("Simulación completada: no se escribió ningún dato.")
            print(
                "Si la revisión está correcta, ejecuta de nuevo con "
                f"--execute --confirm-target {target_config.database}"
            )
            return 0

        migrated = 0
        for table, columns, expected_count in plan:
            copied = _copy_table(
                source_connection,
                target_connection,
                table,
                columns,
                expected_count,
                batch_size,
            )
            target_count = _count_rows(target_connection, table)
            if target_count != expected_count:
                raise RuntimeError(
                    f"Verificación fallida para '{table}': origen={expected_count}, "
                    f"destino={target_count}."
                )
            target_connection.commit()
            migrated += copied
            print(f"  {table}: {copied} registros copiados y verificados")

        print(f"Migración completada: {migrated} registros verificados en TiDB.")
        return 0
    finally:
        if target_connection is not None:
            target_connection.close()
        if source_connection is not None:
            source_connection.close()


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Migra todas las tablas de MySQL local a un esquema TiDB existente."
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Escribe datos. Sin esta opción, solo hace una simulación.",
    )
    parser.add_argument(
        "--confirm-target",
        help="Nombre exacto de la base TiDB que autorizas para la escritura.",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=DEFAULT_BATCH_SIZE,
        help=f"Filas por bloque (por defecto: {DEFAULT_BATCH_SIZE}).",
    )
    args = parser.parse_args()

    logging.basicConfig(
        filename="migracion_tidb.log",
        level=logging.INFO,
        format="%(asctime)s - %(levelname)s - %(message)s",
        encoding="utf-8",
    )
    try:
        return run(
            execute=args.execute,
            confirmed_target=args.confirm_target,
            batch_size=args.batch_size,
        )
    except Exception as exc:
        LOGGER.exception("La migración falló.")
        print(f"Error: {exc}", file=sys.stderr)
        print("Consulta migracion_tidb.log para más detalles.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
