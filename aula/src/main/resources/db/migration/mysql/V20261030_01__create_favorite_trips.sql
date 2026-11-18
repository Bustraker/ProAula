CREATE TABLE IF NOT EXISTS trayecto_favorito (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    origen VARCHAR(255) NOT NULL,
    destino VARCHAR(255) NOT NULL,
    rutas_nombres VARCHAR(760) NOT NULL,
    barrios_recorridos VARCHAR(7800) NOT NULL,
    distancia_estimada_km DOUBLE NULL,
    tiempo_estimado_auto_min INTEGER NULL,
    paradas_count INTEGER NOT NULL,
    fecha_guardado DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_trayecto_favorito_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    INDEX idx_trayecto_favorito_usuario_fecha (usuario_id, fecha_guardado)
);
