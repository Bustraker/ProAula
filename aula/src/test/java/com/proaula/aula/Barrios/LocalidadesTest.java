package com.proaula.aula.Barrios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class LocalidadesTest {

    @Test
    void obtenerTodosLosBarriosDebeDevolverNombresUnicos() {
        List<String> barrios = Localidades.obtenerTodosLosBarrios();
        List<String> unicos = barrios.stream().distinct().collect(Collectors.toList());

        assertEquals(unicos.size(), barrios.size(), "La lista de barrios no debe contener duplicados");
        assertTrue(barrios.size() > 0, "Debe existir al menos un barrio");
    }

    @Test
    void agregarSinDuplicadosDebeIgnorarNulosVaciosYNormalizarVariantes() throws Exception {
        Method method = Localidades.class.getDeclaredMethod("agregarSinDuplicados", List.class, Set.class, List.class);
        method.setAccessible(true);

        List<String> destino = new ArrayList<>();
        Set<String> vistos = new LinkedHashSet<>();
        List<String> origen = Arrays.asList("San Diego", " san diego ", "", null, "Álvaro Gómez", "alvaro gomez", "  La Candelaria ");

        method.invoke(null, destino, vistos, origen);

        assertEquals(List.of("San Diego", "Álvaro Gómez", "La Candelaria"), destino,
            "Debe conservar el nombre original, ignorar vacíos y evitar duplicados por mayúsculas, espacios y tildes");
    }
}
