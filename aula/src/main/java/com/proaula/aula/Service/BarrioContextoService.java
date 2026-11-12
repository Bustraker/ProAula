package com.proaula.aula.Service;

import com.proaula.aula.dto.BarrioContextoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class BarrioContextoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BarrioContextoService.class);

    private final Map<String, BarrioContextoResponse> contextoPorBarrio;
    private final boolean disponible;

    public BarrioContextoService(
            @Value("classpath:static/data/cartografia/barrios-cartografia.geojson")
            Resource archivoContexto) {
        Map<String, BarrioContextoResponse> datos;
        try {
            datos = cargar(archivoContexto);
        } catch (IOException exception) {
            LOGGER.error("No se pudo cargar la cartografía de barrios.", exception);
            datos = Map.of();
        }
        contextoPorBarrio = Map.copyOf(datos);
        disponible = !contextoPorBarrio.isEmpty();
    }

    public Optional<BarrioContextoResponse> obtenerPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(contextoPorBarrio.get(normalizarNombre(nombre)));
    }

    public List<BarrioContextoResponse> obtenerTodos() {
        return contextoPorBarrio.values().stream()
            .sorted((primero, segundo) -> primero.nombreBarrio().compareToIgnoreCase(segundo.nombreBarrio()))
            .toList();
    }

    public boolean disponible() {
        return disponible;
    }

    private static Map<String, BarrioContextoResponse> cargar(Resource recurso) throws IOException {
        JsonNode features;
        try (InputStream contenido = recurso.getInputStream()) {
            features = new ObjectMapper().readTree(contenido).path("features");
        }
        if (!features.isArray()) {
            throw new IOException("El GeoJSON de barrios no contiene una colección de entidades.");
        }

        Map<String, BarrioContextoResponse> datos = new HashMap<>();
        int indice = 0;
        for (JsonNode feature : features) {
            String nombre = feature.path("properties").path("nombre").asText("").trim();
            String clave = normalizarNombre(nombre);
            if (clave.isEmpty()) {
                throw new IOException("La entidad " + indice + " no tiene nombre de barrio.");
            }
            BarrioContextoResponse contexto = new BarrioContextoResponse(
                nombre,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
            if (datos.putIfAbsent(clave, contexto) != null) {
                LOGGER.warn("Se omitió una entidad cartográfica duplicada para el barrio {}.", nombre);
            }
            indice++;
        }
        if (datos.isEmpty()) {
            throw new IOException("El GeoJSON no contiene barrios.");
        }
        return datos;
    }

    public static String normalizarNombre(String nombre) {
        if (nombre == null) {
            return "";
        }
        String clave = Normalizer.normalize(nombre, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
        return clave;
    }
}
