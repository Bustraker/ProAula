ALTER TABLE ruta
    MODIFY COLUMN accesible_movilidad_reducida BOOLEAN NULL DEFAULT NULL;

ALTER TABLE bus
    MODIFY COLUMN accesible_movilidad_reducida BOOLEAN NULL DEFAULT NULL;

ALTER TABLE parada
    MODIFY COLUMN accesible_movilidad_reducida BOOLEAN NULL DEFAULT NULL;
