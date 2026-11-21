package com.proaula.aula.Service;

import com.proaula.aula.Entity.Barrio;
import com.proaula.aula.Repository.BarrioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Catalogo de barrios basado en los nombres y poligonos de la cartografia catastral.
 * Las coordenadas persistidas son puntos representativos de cada poligono, no ubicaciones de paradas.
 */
@Service
public class BarrioService {

    private static final Logger log = LoggerFactory.getLogger(BarrioService.class);
    private static final String LOCALIDAD_NO_VERIFICADA = "Sin localidad verificada";
    private static final double TOLERANCIA_GEOMETRICA = 1e-10;

    @Autowired
    private BarrioRepository barrioRepository;

    private static final Map<String, double[]> COORDENADAS_OFICIALES = new ConcurrentHashMap<>();
    private static final Map<String, String> NOMBRES_CANONICOS = new ConcurrentHashMap<>();
    private static final Map<String, String> LOCALIDADES_OFICIALES = new ConcurrentHashMap<>();
    private static final Map<String, String> ALIAS_A_CANONICO = new ConcurrentHashMap<>();

    static {
        cargarDatosCatastrales();
    }

    private static void cargarDatosCatastrales() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode features = leerFeatures(mapper, "/static/data/cartografia/barrios-cartografia.geojson");
            JsonNode localidades = leerFeatures(mapper, "/static/data/cartografia/localidades-cartografia.geojson");
            Map<String, List<JsonNode>> geometriasPorBarrio = new LinkedHashMap<>();

            int indice = 0;
            for (JsonNode feature : features) {
                String nombre = feature.path("properties").path("nombre").asText("").trim();
                String clave = normalizeStatic(nombre);
                if (clave.isEmpty()) {
                    throw new IOException("La entidad cartográfica " + indice + " no tiene nombre.");
                }
                NOMBRES_CANONICOS.putIfAbsent(clave, nombre);
                geometriasPorBarrio.computeIfAbsent(clave, ignorado -> new ArrayList<>())
                    .add(feature.path("geometry"));
                indice++;
            }
            if (geometriasPorBarrio.isEmpty()) {
                throw new IOException("La cartografía no contiene barrios.");
            }

            geometriasPorBarrio.forEach((clave, geometrias) -> {
                double[] punto = calcularPuntoRepresentativo(geometrias);
                if (punto == null) {
                    log.warn("El barrio catastral {} no tiene un punto representativo calculable.", NOMBRES_CANONICOS.get(clave));
                    LOCALIDADES_OFICIALES.put(clave, LOCALIDAD_NO_VERIFICADA);
                    return;
                }
                COORDENADAS_OFICIALES.put(clave, punto);
                LOCALIDADES_OFICIALES.put(clave, localidadParaPunto(localidades, punto));
            });

            if (!NOMBRES_CANONICOS.containsKey(normalizeStatic("ZARAGOCILLA"))) {
                log.warn("No se encontró ZARAGOCILLA en la cartografía catastral; no se registrará el alias SARAGOCILLA.");
            } else {
                registrarAlias("Saragocilla", "ZARAGOCILLA");
            }
            registrarAlias("Simón Bolívar", "URBANIZACION SIMON BOLIVAR");
        } catch (IOException ex) {
            log.error("No se pudo cargar la cartografía catastral de barrios.", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static JsonNode leerFeatures(ObjectMapper mapper, String ruta) throws IOException {
        try (InputStream entrada = BarrioService.class.getResourceAsStream(ruta)) {
            if (entrada == null) {
                throw new IOException("No se encontró el recurso " + ruta);
            }
            JsonNode coleccion = mapper.readTree(entrada);
            JsonNode features = coleccion.path("features");
            if (!"FeatureCollection".equals(coleccion.path("type").asText()) || !features.isArray()) {
                throw new IOException("El recurso " + ruta + " no es una colección GeoJSON válida.");
            }
            return features;
        }
    }

    private static void registrarAlias(String alias, String nombreCanonico) {
        String claveAlias = normalizeStatic(alias);
        String claveCanonica = normalizeStatic(nombreCanonico);
        if (NOMBRES_CANONICOS.containsKey(claveCanonica) && !NOMBRES_CANONICOS.containsKey(claveAlias)) {
            ALIAS_A_CANONICO.put(claveAlias, claveCanonica);
        }
    }

    private static double[] calcularPuntoRepresentativo(List<JsonNode> geometrias) {
        double areaTotal = 0;
        double sumaLongitudes = 0;
        double sumaLatitudes = 0;
        for (JsonNode geometria : geometrias) {
            for (JsonNode poligono : extraerPoligonos(geometria)) {
                AreaCentroide centroide = centroidePoligono(poligono);
                if (centroide != null) {
                    areaTotal += centroide.area();
                    sumaLongitudes += centroide.longitud() * centroide.area();
                    sumaLatitudes += centroide.latitud() * centroide.area();
                }
            }
        }
        if (areaTotal > 0) {
            double longitud = sumaLongitudes / areaTotal;
            double latitud = sumaLatitudes / areaTotal;
            if (geometrias.stream().anyMatch(geometria -> geometriaContienePunto(geometria, longitud, latitud))) {
                return new double[]{latitud, longitud};
            }
        }

        PuntoInterior mejor = null;
        for (JsonNode geometria : geometrias) {
            for (JsonNode poligono : extraerPoligonos(geometria)) {
                PuntoInterior candidato = puntoInteriorPoligono(poligono);
                if (candidato != null && (mejor == null || candidato.anchura() > mejor.anchura())) {
                    mejor = candidato;
                }
            }
        }
        return mejor == null ? null : new double[]{mejor.latitud(), mejor.longitud()};
    }

    private static List<JsonNode> extraerPoligonos(JsonNode geometria) {
        String tipo = geometria.path("type").asText();
        JsonNode coordenadas = geometria.path("coordinates");
        if ("Polygon".equals(tipo)) {
            return coordenadas.isArray() ? List.of(coordenadas) : List.of();
        }
        if ("MultiPolygon".equals(tipo) && coordenadas.isArray()) {
            List<JsonNode> poligonos = new ArrayList<>();
            coordenadas.forEach(poligonos::add);
            return poligonos;
        }
        return List.of();
    }

    private static AreaCentroide centroidePoligono(JsonNode anillos) {
        if (!anillos.isArray() || anillos.isEmpty()) {
            return null;
        }
        AreaCentroide exterior = centroideAnillo(anillos.get(0));
        if (exterior == null) {
            return null;
        }
        double area = exterior.area();
        double sumaLongitudes = exterior.longitud() * area;
        double sumaLatitudes = exterior.latitud() * area;
        for (int indice = 1; indice < anillos.size(); indice++) {
            AreaCentroide hueco = centroideAnillo(anillos.get(indice));
            if (hueco != null) {
                area -= hueco.area();
                sumaLongitudes -= hueco.longitud() * hueco.area();
                sumaLatitudes -= hueco.latitud() * hueco.area();
            }
        }
        if (area <= 0) {
            return null;
        }
        return new AreaCentroide(area, sumaLongitudes / area, sumaLatitudes / area);
    }

    private static AreaCentroide centroideAnillo(JsonNode anillo) {
        if (!anillo.isArray() || anillo.size() < 4) {
            return null;
        }
        double areaConSigno = 0;
        double sumaLongitudes = 0;
        double sumaLatitudes = 0;
        for (int indice = 0; indice < anillo.size(); indice++) {
            JsonNode actual = anillo.get(indice);
            JsonNode siguiente = anillo.get((indice + 1) % anillo.size());
            if (!esPosicion(actual) || !esPosicion(siguiente)) {
                return null;
            }
            double longitudActual = actual.get(0).asDouble();
            double latitudActual = actual.get(1).asDouble();
            double longitudSiguiente = siguiente.get(0).asDouble();
            double latitudSiguiente = siguiente.get(1).asDouble();
            double productoCruzado = longitudActual * latitudSiguiente - longitudSiguiente * latitudActual;
            areaConSigno += productoCruzado;
            sumaLongitudes += (longitudActual + longitudSiguiente) * productoCruzado;
            sumaLatitudes += (latitudActual + latitudSiguiente) * productoCruzado;
        }
        if (Math.abs(areaConSigno) < 1e-14) {
            return null;
        }
        return new AreaCentroide(
            Math.abs(areaConSigno) / 2,
            sumaLongitudes / (3 * areaConSigno),
            sumaLatitudes / (3 * areaConSigno));
    }

    private static PuntoInterior puntoInteriorPoligono(JsonNode anillos) {
        if (!anillos.isArray() || anillos.isEmpty() || !anillos.get(0).isArray()) {
            return null;
        }
        double latitudMinima = Double.POSITIVE_INFINITY;
        double latitudMaxima = Double.NEGATIVE_INFINITY;
        for (JsonNode posicion : anillos.get(0)) {
            if (!esPosicion(posicion)) {
                continue;
            }
            double latitud = posicion.get(1).asDouble();
            latitudMinima = Math.min(latitudMinima, latitud);
            latitudMaxima = Math.max(latitudMaxima, latitud);
        }
        if (!Double.isFinite(latitudMinima) || latitudMaxima <= latitudMinima) {
            return null;
        }

        PuntoInterior mejor = null;
        for (int paso = 1; paso < 256; paso++) {
            double latitud = latitudMinima + (latitudMaxima - latitudMinima) * paso / 256;
            List<Double> intersecciones = new ArrayList<>();
            for (JsonNode anillo : anillos) {
                for (int indice = 0, anterior = anillo.size() - 1; indice < anillo.size(); anterior = indice++) {
                    JsonNode a = anillo.get(anterior);
                    JsonNode b = anillo.get(indice);
                    if (!esPosicion(a) || !esPosicion(b)) {
                        continue;
                    }
                    double latitudA = a.get(1).asDouble();
                    double latitudB = b.get(1).asDouble();
                    if ((latitudA > latitud) != (latitudB > latitud)) {
                        double longitud = a.get(0).asDouble()
                            + (latitud - latitudA) * (b.get(0).asDouble() - a.get(0).asDouble())
                                / (latitudB - latitudA);
                        intersecciones.add(longitud);
                    }
                }
            }
            intersecciones.sort(Double::compareTo);
            for (int indice = 0; indice + 1 < intersecciones.size(); indice += 2) {
                double anchura = (intersecciones.get(indice + 1) - intersecciones.get(indice))
                    * Math.cos(Math.toRadians(latitud));
                double longitud = (intersecciones.get(indice + 1) + intersecciones.get(indice)) / 2;
                if (anchura > 0 && (mejor == null || anchura > mejor.anchura())
                        && poligonoContienePunto(anillos, longitud, latitud)) {
                    mejor = new PuntoInterior(anchura, longitud, latitud);
                }
            }
        }
        return mejor;
    }

    private static boolean geometriaContienePunto(JsonNode geometria, double longitud, double latitud) {
        return extraerPoligonos(geometria).stream()
            .anyMatch(poligono -> poligonoContienePunto(poligono, longitud, latitud));
    }

    private static boolean poligonoContienePunto(JsonNode anillos, double longitud, double latitud) {
        if (!anillos.isArray() || anillos.isEmpty() || !puntoEnAnillo(longitud, latitud, anillos.get(0))) {
            return false;
        }
        for (int indice = 1; indice < anillos.size(); indice++) {
            if (puntoEnAnillo(longitud, latitud, anillos.get(indice))) {
                return false;
            }
        }
        return true;
    }

    private static boolean puntoEnAnillo(double longitud, double latitud, JsonNode anillo) {
        if (!anillo.isArray() || anillo.size() < 3) {
            return false;
        }
        boolean dentro = false;
        for (int indice = 0, anterior = anillo.size() - 1; indice < anillo.size(); anterior = indice++) {
            JsonNode actual = anillo.get(indice);
            JsonNode previo = anillo.get(anterior);
            if (!esPosicion(actual) || !esPosicion(previo)) {
                continue;
            }
            double longitudActual = actual.get(0).asDouble();
            double latitudActual = actual.get(1).asDouble();
            double longitudPrevia = previo.get(0).asDouble();
            double latitudPrevia = previo.get(1).asDouble();
            double productoCruzado = (longitud - longitudPrevia) * (latitudActual - latitudPrevia)
                - (latitud - latitudPrevia) * (longitudActual - longitudPrevia);
            if (Math.abs(productoCruzado) <= TOLERANCIA_GEOMETRICA
                    && longitud >= Math.min(longitudPrevia, longitudActual) - TOLERANCIA_GEOMETRICA
                    && longitud <= Math.max(longitudPrevia, longitudActual) + TOLERANCIA_GEOMETRICA
                    && latitud >= Math.min(latitudPrevia, latitudActual) - TOLERANCIA_GEOMETRICA
                    && latitud <= Math.max(latitudPrevia, latitudActual) + TOLERANCIA_GEOMETRICA) {
                return true;
            }
            if ((latitudActual > latitud) != (latitudPrevia > latitud)
                    && longitud < (longitudPrevia - longitudActual) * (latitud - latitudActual)
                        / (latitudPrevia - latitudActual) + longitudActual) {
                dentro = !dentro;
            }
        }
        return dentro;
    }

    private static boolean esPosicion(JsonNode nodo) {
        return nodo != null && nodo.isArray() && nodo.size() >= 2
            && nodo.get(0).isNumber() && nodo.get(1).isNumber();
    }

    private static String localidadParaPunto(JsonNode localidades, double[] punto) {
        Set<String> coincidencias = new TreeSet<>();
        for (JsonNode feature : localidades) {
            String nombre = feature.path("properties").path("nombre").asText("").trim();
            if (!nombre.isEmpty()
                    && geometriaContienePunto(feature.path("geometry"), punto[1], punto[0])) {
                coincidencias.add(nombre);
            }
        }
        return coincidencias.size() == 1 ? coincidencias.iterator().next() : LOCALIDAD_NO_VERIFICADA;
    }

    public static String normalizeStatic(String nombre) {
        if (nombre == null) {
            return "";
        }
        return Normalizer.normalize(nombre.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace("ñ", "n")
            .replace("Ñ", "N")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }

    private String normalize(String nombre) {
        return normalizeStatic(nombre);
    }

    private static String claveCanonica(String nombre) {
        String normalizado = normalizeStatic(nombre);
        return ALIAS_A_CANONICO.getOrDefault(normalizado, normalizado);
    }

    public String obtenerLocalidad(String nombre) {
        return LOCALIDADES_OFICIALES.getOrDefault(claveCanonica(nombre), LOCALIDAD_NO_VERIFICADA);
    }

    public Optional<Barrio> obtenerPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        String clave = claveCanonica(nombre);
        if (!NOMBRES_CANONICOS.containsKey(clave)) {
            return Optional.empty();
        }
        return barrioRepository.findAll().stream()
            .filter(b -> normalize(b.getNombre()).equals(clave))
            .findFirst();
    }

    public List<Barrio> obtenerTodos() {
        return barrioRepository.findAll().stream()
            .sorted(Comparator.comparing(Barrio::getNombre, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /**
     * Sincroniza un registro por barrio cartografiado y elimina nombres que ya no están en la fuente.
     */
    @Transactional
    public void inicializarBarrios() {
        List<Barrio> existentes = barrioRepository.findAll();
        Map<String, Barrio> existentesPorClave = new HashMap<>();
        for (Barrio existente : existentes) {
            existentesPorClave.putIfAbsent(normalize(existente.getNombre()), existente);
        }

        List<Barrio> barriosParaGuardar = new ArrayList<>();
        Set<Long> idsConservados = new HashSet<>();
        for (String clave : new TreeSet<>(NOMBRES_CANONICOS.keySet())) {
            String nombreCanonico = NOMBRES_CANONICOS.get(clave);
            Barrio barrio = existentesPorClave.getOrDefault(clave, new Barrio());
            if (barrio.getId() != null) {
                idsConservados.add(barrio.getId());
            }
            barrio.setNombre(nombreCanonico);
            barrio.setLocalidad(obtenerLocalidad(nombreCanonico));

            double[] punto = COORDENADAS_OFICIALES.get(clave);
            barrio.setLatitud(punto == null ? null : punto[0]);
            barrio.setLongitud(punto == null ? null : punto[1]);
            barriosParaGuardar.add(barrio);
        }

        List<Barrio> obsoletos = existentes.stream()
            .filter(barrio -> barrio.getId() == null || !idsConservados.contains(barrio.getId()))
            .toList();
        if (!obsoletos.isEmpty()) {
            barrioRepository.deleteAll(obsoletos);
        }
        barrioRepository.saveAll(barriosParaGuardar);
        log.info("Barrios sincronizados desde GeoJSON catastral: {}", barriosParaGuardar.size());
    }

    public Map<String, double[]> obtenerCoordenadas(List<String> nombresBarrios) {
        Map<String, double[]> resultado = new HashMap<>();
        if (nombresBarrios == null) {
            return resultado;
        }

        for (String nombre : nombresBarrios) {
            if (nombre == null || nombre.isBlank()) {
                continue;
            }
            double[] punto = COORDENADAS_OFICIALES.get(claveCanonica(nombre));
            if (punto != null) {
                resultado.put(nombre, punto.clone());
            }
        }
        return resultado;
    }

    public boolean tieneCoordenadaCatastral(String nombre) {
        return nombre != null && !nombre.isBlank()
            && COORDENADAS_OFICIALES.containsKey(claveCanonica(nombre));
    }

    public String obtenerNombreCanonico(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "";
        }
        return NOMBRES_CANONICOS.getOrDefault(claveCanonica(nombre), "");
    }

    private record AreaCentroide(double area, double longitud, double latitud) {}

    private record PuntoInterior(double anchura, double longitud, double latitud) {}
}
