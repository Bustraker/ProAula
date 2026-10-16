CREATE TABLE IF NOT EXISTS reporte_ocupacion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bus_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    nivel VARCHAR(10) NOT NULL,
    cantidad_pasajeros INT NULL,
    comentario VARCHAR(255) NULL,
    reportado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_reporte_ocupacion_bus FOREIGN KEY (bus_id) REFERENCES bus(id),
    CONSTRAINT fk_reporte_ocupacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS reporte_posicion (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bus_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    latitud DECIMAL(10,7) NOT NULL,
    longitud DECIMAL(10,7) NOT NULL,
    precision_mt DECIMAL(6,2) NULL,
    velocidad_kmh DECIMAL(5,2) NULL,
    nivel_confianza VARCHAR(10) NOT NULL DEFAULT 'MEDIA',
    reportado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valido BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_reporte_posicion_bus FOREIGN KEY (bus_id) REFERENCES bus(id),
    CONSTRAINT fk_reporte_posicion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS incidente (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    ruta_id BIGINT NULL,
    bus_id BIGINT NULL,
    tipo VARCHAR(30) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    descripcion TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_reporte TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_resolucion TIMESTAMP NULL,
    comentario_admin TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_incidente_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_incidente_ruta FOREIGN KEY (ruta_id) REFERENCES ruta(id),
    CONSTRAINT fk_incidente_bus FOREIGN KEY (bus_id) REFERENCES bus(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS favorito (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id BIGINT NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    ruta_id BIGINT NULL,
    parada_id BIGINT NULL,
    etiqueta VARCHAR(100) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorito_usuario_ruta (usuario_id, ruta_id),
    UNIQUE KEY uk_favorito_usuario_parada (usuario_id, parada_id),
    CONSTRAINT fk_favorito_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_favorito_ruta FOREIGN KEY (ruta_id) REFERENCES ruta(id),
    CONSTRAINT fk_favorito_parada FOREIGN KEY (parada_id) REFERENCES parada(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS demanda_historica (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ruta_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    dia_semana VARCHAR(12) NOT NULL,
    franja_horaria VARCHAR(24) NOT NULL,
    total_consultas INT NOT NULL DEFAULT 0,
    reportes_ocupacion_baja INT NULL DEFAULT 0,
    reportes_ocupacion_media INT NULL DEFAULT 0,
    reportes_ocupacion_alta INT NULL DEFAULT 0,
    promedio_ocupacion DECIMAL(5,2) NULL,
    nivel_demanda_estimado VARCHAR(10) NULL,
    datos_suficientes BOOLEAN NOT NULL DEFAULT FALSE,
    fuente_datos VARCHAR(20) NOT NULL DEFAULT 'CONSOLIDADO',
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_demanda_historica_ruta_fecha_franja (ruta_id, fecha, franja_horaria),
    CONSTRAINT fk_demanda_historica_ruta FOREIGN KEY (ruta_id) REFERENCES ruta(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tarifa (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ruta_id BIGINT NOT NULL,
    tipo_usuario VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    valor DECIMAL(10,2) NOT NULL,
    vigencia_desde TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    vigencia_hasta TIMESTAMP NULL,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_tarifa_ruta FOREIGN KEY (ruta_id) REFERENCES ruta(id)
) ENGINE=InnoDB;
