CREATE TABLE IF NOT EXISTS favorito (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    ruta_id BIGINT NULL,
    parada_id BIGINT NULL,
    fecha_creacion DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_favorito_usuario_ruta UNIQUE (usuario_id, ruta_id),
    CONSTRAINT uk_favorito_usuario_parada UNIQUE (usuario_id, parada_id),
    CONSTRAINT chk_favorito_objetivo CHECK (
        (tipo = 'RUTA' AND ruta_id IS NOT NULL AND parada_id IS NULL)
        OR (tipo = 'PARADA' AND parada_id IS NOT NULL AND ruta_id IS NULL)
    ),
    CONSTRAINT fk_favorito_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorito_ruta FOREIGN KEY (ruta_id) REFERENCES ruta (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorito_parada FOREIGN KEY (parada_id) REFERENCES parada (id) ON DELETE CASCADE,
    INDEX idx_favorito_usuario_fecha (usuario_id, fecha_creacion)
);
