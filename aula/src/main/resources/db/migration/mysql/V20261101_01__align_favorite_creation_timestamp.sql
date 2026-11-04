SET @favorito_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'favorito'
       AND COLUMN_NAME = 'fecha_creacion') = 0,
    'ALTER TABLE favorito ADD COLUMN fecha_creacion DATETIME NULL',
    'SELECT 1'
);
PREPARE favorito_stmt FROM @favorito_ddl;
EXECUTE favorito_stmt;
DEALLOCATE PREPARE favorito_stmt;

SET @favorito_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'favorito'
       AND COLUMN_NAME = 'created_at') > 0,
    'UPDATE favorito SET fecha_creacion = COALESCE(fecha_creacion, created_at) WHERE fecha_creacion IS NULL',
    'UPDATE favorito SET fecha_creacion = CURRENT_TIMESTAMP WHERE fecha_creacion IS NULL'
);
PREPARE favorito_stmt FROM @favorito_ddl;
EXECUTE favorito_stmt;
DEALLOCATE PREPARE favorito_stmt;

ALTER TABLE favorito
    MODIFY COLUMN fecha_creacion DATETIME NOT NULL;

SET @favorito_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'favorito'
       AND INDEX_NAME = 'idx_favorito_usuario_fecha') = 0,
    'CREATE INDEX idx_favorito_usuario_fecha ON favorito (usuario_id, fecha_creacion)',
    'SELECT 1'
);
PREPARE favorito_stmt FROM @favorito_ddl;
EXECUTE favorito_stmt;
DEALLOCATE PREPARE favorito_stmt;
