SET @admin_approval_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_code_requests'
       AND COLUMN_NAME = 'nombres') = 0,
    'ALTER TABLE admin_code_requests ADD COLUMN nombres VARCHAR(100) NULL',
    'SELECT 1'
);
PREPARE admin_approval_stmt FROM @admin_approval_ddl;
EXECUTE admin_approval_stmt;
DEALLOCATE PREPARE admin_approval_stmt;

SET @admin_approval_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_code_requests'
       AND COLUMN_NAME = 'apellidos') = 0,
    'ALTER TABLE admin_code_requests ADD COLUMN apellidos VARCHAR(100) NULL',
    'SELECT 1'
);
PREPARE admin_approval_stmt FROM @admin_approval_ddl;
EXECUTE admin_approval_stmt;
DEALLOCATE PREPARE admin_approval_stmt;

SET @admin_approval_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_code_requests'
       AND COLUMN_NAME = 'created_by') = 0,
    'ALTER TABLE admin_code_requests ADD COLUMN created_by VARCHAR(20) NULL',
    'SELECT 1'
);
PREPARE admin_approval_stmt FROM @admin_approval_ddl;
EXECUTE admin_approval_stmt;
DEALLOCATE PREPARE admin_approval_stmt;

SET @admin_approval_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_codes'
       AND COLUMN_NAME = 'email') = 0,
    'ALTER TABLE admin_codes ADD COLUMN email VARCHAR(254) NULL',
    'SELECT 1'
);
PREPARE admin_approval_stmt FROM @admin_approval_ddl;
EXECUTE admin_approval_stmt;
DEALLOCATE PREPARE admin_approval_stmt;

SET @admin_approval_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_codes'
       AND COLUMN_NAME = 'registration_request_id') = 0,
    'ALTER TABLE admin_codes ADD COLUMN registration_request_id BIGINT NULL',
    'SELECT 1'
);
PREPARE admin_approval_stmt FROM @admin_approval_ddl;
EXECUTE admin_approval_stmt;
DEALLOCATE PREPARE admin_approval_stmt;

SET @admin_approval_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_codes'
       AND INDEX_NAME = 'uk_admin_code_registration_request') = 0,
    'CREATE UNIQUE INDEX uk_admin_code_registration_request ON admin_codes (registration_request_id)',
    'SELECT 1'
);
PREPARE admin_approval_stmt FROM @admin_approval_ddl;
EXECUTE admin_approval_stmt;
DEALLOCATE PREPARE admin_approval_stmt;

SET @admin_approval_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_codes'
       AND INDEX_NAME = 'idx_admin_code_email') = 0,
    'CREATE INDEX idx_admin_code_email ON admin_codes (email)',
    'SELECT 1'
);
PREPARE admin_approval_stmt FROM @admin_approval_ddl;
EXECUTE admin_approval_stmt;
DEALLOCATE PREPARE admin_approval_stmt;

SET @admin_approval_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_codes'
       AND CONSTRAINT_NAME = 'fk_admin_code_registration_request'
       AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 0,
    'ALTER TABLE admin_codes ADD CONSTRAINT fk_admin_code_registration_request FOREIGN KEY (registration_request_id) REFERENCES admin_code_requests(id)',
    'SELECT 1'
);
PREPARE admin_approval_stmt FROM @admin_approval_ddl;
EXECUTE admin_approval_stmt;
DEALLOCATE PREPARE admin_approval_stmt;
