CREATE TABLE IF NOT EXISTS admin_access_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_user_id BIGINT NOT NULL,
    username VARCHAR(20) NOT NULL,
    nombres VARCHAR(255) NULL,
    apellidos VARCHAR(255) NULL,
    email VARCHAR(254) NOT NULL,
    admin_code_id BIGINT NOT NULL,
    code_owner_email VARCHAR(254) NULL,
    accessed_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_admin_access_log_accessed_at (accessed_at, id)
);
