package com.proaula.aula.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.proaula.aula.Entity.Bus;
import com.proaula.aula.Entity.Ruta;

public final class BusConsultaMapper {
    private BusConsultaMapper() {
    }

    public static List<BusConsultaDto> publicRoutes(List<Bus> buses) {
        Map<String, BusConsultaDto> routes = new LinkedHashMap<>();
        for (Bus bus : buses) {
            Ruta ruta = bus.getRuta();
            if (ruta == null || ruta.getNombre() == null || ruta.getNombre().isBlank()) {
                continue;
            }

            String key = ruta.getId() != null
                    ? "id:" + ruta.getId()
                    : "nombre:" + ruta.getNombre().trim().toLowerCase(Locale.ROOT);
            routes.putIfAbsent(key, new BusConsultaDto(
                    null, null, null, null, null, ruta.getNombre(), neighborhoods(ruta), null, null));
        }
        return new ArrayList<>(routes.values());
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
