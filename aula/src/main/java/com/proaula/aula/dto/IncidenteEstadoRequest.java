package com.proaula.aula.dto;

import jakarta.validation.constraints.NotBlank;

public class IncidenteEstadoRequest {

    @NotBlank(message = "Indica el estado del incidente.")
    private String estado;

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
