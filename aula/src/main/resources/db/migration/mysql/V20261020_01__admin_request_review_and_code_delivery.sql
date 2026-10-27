SET @admin_request_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_code_requests'
       AND COLUMN_NAME = 'motivo') = 0,
    'ALTER TABLE admin_code_requests ADD COLUMN motivo VARCHAR(500) NULL',
    'SELECT 1'
);
PREPARE admin_request_stmt FROM @admin_request_ddl;
EXECUTE admin_request_stmt;
DEALLOCATE PREPARE admin_request_stmt;

SET @admin_request_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_code_requests'
       AND COLUMN_NAME = 'created_by_name') = 0,
    'ALTER TABLE admin_code_requests ADD COLUMN created_by_name VARCHAR(201) NULL',
    'SELECT 1'
);
PREPARE admin_request_stmt FROM @admin_request_ddl;
EXECUTE admin_request_stmt;
DEALLOCATE PREPARE admin_request_stmt;

SET @admin_request_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_code_requests'
       AND COLUMN_NAME = 'code_email_sent_at') = 0,
    'ALTER TABLE admin_code_requests ADD COLUMN code_email_sent_at TIMESTAMP NULL',
    'SELECT 1'
);
PREPARE admin_request_stmt FROM @admin_request_ddl;
EXECUTE admin_request_stmt;
DEALLOCATE PREPARE admin_request_stmt;

SET @admin_request_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'admin_code_requests'
       AND COLUMN_NAME = 'code_email_sent_by') = 0,
    'ALTER TABLE admin_code_requests ADD COLUMN code_email_sent_by VARCHAR(20) NULL',
    'SELECT 1'
);
PREPARE admin_request_stmt FROM @admin_request_ddl;
EXECUTE admin_request_stmt;
DEALLOCATE PREPARE admin_request_stmt;
