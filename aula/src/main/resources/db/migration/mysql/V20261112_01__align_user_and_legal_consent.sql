SET @usuario_email_verified_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'usuario'
       AND COLUMN_NAME = 'email_verified') = 0,
    'ALTER TABLE usuario ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE',
    'SELECT 1'
);
PREPARE usuario_email_verified_stmt FROM @usuario_email_verified_ddl;
EXECUTE usuario_email_verified_stmt;
DEALLOCATE PREPARE usuario_email_verified_stmt;

SET @legal_consent_revocation_ddl = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'legal_consent'
       AND COLUMN_NAME = 'revoked_at') = 0,
    'ALTER TABLE legal_consent ADD COLUMN revoked_at DATETIME NULL',
    'SELECT 1'
);
PREPARE legal_consent_revocation_stmt FROM @legal_consent_revocation_ddl;
EXECUTE legal_consent_revocation_stmt;
DEALLOCATE PREPARE legal_consent_revocation_stmt;
