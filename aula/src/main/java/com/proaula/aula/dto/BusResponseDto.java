package com.proaula.aula.dto;

import com.proaula.aula.Entity.Bus;

public record BusResponseDto(
        Long id,
        String placa,
        String modelo,
        String color,
        String conductor,
        String numeroInterno,
        String marca,
        Integer capacidadPasajeros,
        Integer anio,
        String estado,
        Boolean accesibleMovilidadReducida,
        String nivelOcupacionActual,
        Long rutaId,
        String rutaNombre,
        Long agenciaId,
        String agenciaNombre) {

    public static BusResponseDto from(Bus bus) {
        return new BusResponseDto(
                bus.getId(),
                bus.getPlaca(),
                bus.getModelo(),
                bus.getColor(),
                bus.getConductor(),
                bus.getNumeroInterno(),
                bus.getMarca(),
                bus.getCapacidadPasajeros(),
                bus.getAnio(),
                bus.getEstado() == null ? null : bus.getEstado().name(),
                bus.getAccesibleMovilidadReducida(),
                bus.getNivelOcupacionActual() == null ? null : bus.getNivelOcupacionActual().name(),
                bus.getRuta() == null ? null : bus.getRuta().getId(),
                bus.getRuta() == null ? null : bus.getRuta().getNombre(),
                bus.getAgencia() == null ? null : bus.getAgencia().getId(),
                bus.getAgencia() == null ? null : bus.getAgencia().getNombre());
    }
}
