package com.proaula.aula.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BusConsultaDto {
    private final Long id;
    private final String placa;
    private final String modelo;
    private final String color;
    private final String conductor;
    private final String rutaNombre;
    private final List<String> barrios;
    private final String horaAproximada;
    private final Boolean rutaVerificada;

    public BusConsultaDto(Long id, String placa, String modelo, String color, String conductor,
                          String rutaNombre, List<String> barrios, String horaAproximada,
                          Boolean rutaVerificada) {
        this.id = id;
        this.placa = placa;
        this.modelo = modelo;
        this.color = color;
        this.conductor = conductor;
        this.rutaNombre = rutaNombre;
        this.barrios = barrios;
        this.horaAproximada = horaAproximada;
        this.rutaVerificada = rutaVerificada;
    }

    public Long getId() {
        return id;
    }

    public String getPlaca() {
        return placa;
    }

    public String getModelo() {
        return modelo;
    }

    public String getColor() {
        return color;
    }

    public String getConductor() {
        return conductor;
    }

    public String getRutaNombre() {
        return rutaNombre;
    }

    public List<String> getBarrios() {
        return barrios;
    }

    public String getHoraAproximada() {
        return horaAproximada;
    }

    public Boolean getRutaVerificada() {
        return rutaVerificada;
    }
}
