CREATE TABLE IF NOT EXISTS notificacion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    mensaje TEXT NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    leido BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_leido DATETIME NULL,
    canal_envio VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notificacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    INDEX idx_notificacion_usuario_leido (usuario_id, leido),
    INDEX idx_notificacion_usuario_fecha (usuario_id, id DESC)
);

SET @notificacion_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notificacion'
       AND COLUMN_NAME = 'canal_envio') = 0,
    'ALTER TABLE notificacion ADD COLUMN canal_envio VARCHAR(20) NOT NULL DEFAULT ''IN_APP''',
    'SELECT 1'
);
PREPARE notificacion_stmt FROM @notificacion_ddl;
EXECUTE notificacion_stmt;
DEALLOCATE PREPARE notificacion_stmt;

SET @notificacion_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notificacion'
       AND COLUMN_NAME = 'fecha_creacion') = 0,
    'ALTER TABLE notificacion ADD COLUMN fecha_creacion DATETIME NULL',
    'SELECT 1'
);
PREPARE notificacion_stmt FROM @notificacion_ddl;
EXECUTE notificacion_stmt;
DEALLOCATE PREPARE notificacion_stmt;

UPDATE notificacion
SET canal_envio = 'IN_APP'
WHERE canal_envio IS NULL OR TRIM(canal_envio) = '';

UPDATE notificacion
SET fecha_creacion = CURRENT_TIMESTAMP
WHERE fecha_creacion IS NULL;

ALTER TABLE notificacion
    MODIFY COLUMN canal_envio VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    MODIFY COLUMN fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

SET @notificacion_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notificacion'
       AND INDEX_NAME = 'idx_notificacion_usuario_leido') = 0,
    'CREATE INDEX idx_notificacion_usuario_leido ON notificacion (usuario_id, leido)',
    'SELECT 1'
);
PREPARE notificacion_stmt FROM @notificacion_ddl;
EXECUTE notificacion_stmt;
DEALLOCATE PREPARE notificacion_stmt;

SET @notificacion_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'notificacion'
       AND INDEX_NAME = 'idx_notificacion_usuario_fecha') = 0,
    'CREATE INDEX idx_notificacion_usuario_fecha ON notificacion (usuario_id, id DESC)',
    'SELECT 1'
);
PREPARE notificacion_stmt FROM @notificacion_ddl;
EXECUTE notificacion_stmt;
DEALLOCATE PREPARE notificacion_stmt;
