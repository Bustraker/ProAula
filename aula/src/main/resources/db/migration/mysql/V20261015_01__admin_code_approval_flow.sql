CREATE TABLE admin_code_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    request_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP NULL,
    decided_by VARCHAR(20) NULL,
    PRIMARY KEY (id),
    INDEX idx_admin_code_request_status_created (request_status, created_at),
    INDEX idx_admin_code_request_email (email)
);

CREATE TABLE admin_invite_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code_hash VARCHAR(64) NOT NULL,
    email VARCHAR(254) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_invite_code_hash (code_hash),
    INDEX idx_admin_invite_email (email),
    INDEX idx_admin_invite_expiry (expires_at)
);

CREATE TABLE admin_push_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    admin_email VARCHAR(254) NOT NULL,
    device_token TEXT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_push_token_hash (token_hash),
    INDEX idx_admin_push_email (admin_email)
);
