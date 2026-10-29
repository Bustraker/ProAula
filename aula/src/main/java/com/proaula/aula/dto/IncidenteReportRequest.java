package com.proaula.aula.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class IncidenteReportRequest {

    @NotNull(message = "Selecciona el tipo de incidente.")
    private TipoIncidente tipo;

    @NotNull(message = "Selecciona la ruta afectada.")
    private Long rutaId;

    @NotBlank(message = "Escribe una descripción del incidente.")
    @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres.")
    private String descripcion;

    public TipoIncidente getTipo() {
        return tipo;
    }

    public void setTipo(TipoIncidente tipo) {
        this.tipo = tipo;
    }

    public Long getRutaId() {
        return rutaId;
    }

    public void setRutaId(Long rutaId) {
        this.rutaId = rutaId;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public enum TipoIncidente {
        RETRASO("Retraso"),
        ACCIDENTE("Accidente"),
        FALLA_SERVICIO("Falla del bus o del servicio"),
        RUTA_DESVIADA("Desvío de ruta"),
        OTRO("Otro");

        private final String etiqueta;

        TipoIncidente(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String getEtiqueta() {
            return etiqueta;
        }
    }
}
