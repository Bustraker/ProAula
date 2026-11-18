CREATE TABLE IF NOT EXISTS legal_consent (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    terms_version VARCHAR(40) NOT NULL,
    privacy_version VARCHAR(40) NOT NULL,
    accepted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    adult_confirmed BOOLEAN NOT NULL,
    consent_source VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_legal_consent_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    INDEX idx_legal_consent_current (usuario_id, terms_version, privacy_version, adult_confirmed)
);
