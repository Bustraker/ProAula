package com.proaula.aula.dto;

import java.util.List;
import java.util.Objects;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.Ruta;

public final class BusConsultaMapper {
    private BusConsultaMapper() {
    }

    public static List<BusConsultaDto> authenticatedBuses(List<Bus> buses) {
        return buses.stream()
                .map(BusConsultaMapper::authenticatedBus)
                .toList();
    }

    private static BusConsultaDto authenticatedBus(Bus bus) {
        Ruta ruta = bus.getRuta();
        return new BusConsultaDto(
                bus.getId(),
                bus.getPlaca(),
                bus.getModelo(),
                bus.getColor(),
                bus.getConductor(),
                ruta == null ? null : ruta.getNombre(),
                ruta == null ? List.of() : neighborhoods(ruta),
                ruta == null || ruta.getHoraAproximada() == null
                        ? null : ruta.getHoraAproximada().toString(),
                ruta == null ? null : ruta.getVerificada());
    }

    private static List<String> neighborhoods(Ruta ruta) {
        List<String> source = ruta.isOrdenBarriosConfirmado()
                ? ruta.getBarriosOrdenados()
                : ruta.getBarrios();
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim())
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
