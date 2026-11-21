package com.proaula.aula.Service;

import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Repository.RutaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Order(1)
public class TpcRouteReferenceSeederService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TpcRouteReferenceSeederService.class);
    private static final String SOURCE_NOTE =
        "Fuente: listado compartido por el usuario para noviembre de 2026; no se validó de forma independiente con una fuente oficial.";
    private static final List<String> LEGACY_PROVISIONAL_CODES =
        List.of("RUT-CAMP-SOC-BCG-CG", "RUT-POZ-TORICES", "RUT-37-SIM-CENTRO", "RUT-30A-NB-CENTRO");

    private static final List<RouteReference> ROUTES = List.of(
        new RouteReference("CAM-CEN", "CAMPESTRE-CENTRO", "COOINTRACAR",
            List.of("EL CAMPESTRE", "EL SOCORRO", "CENTRO"),
            "Barrio El Campestre → El Socorro → Av. Blas de Lezo → Av. Pedro de Heredia → Bazurto → Centro Histórico (Plaza de los Mártires)",
            "Centro → Bazurto → Av. Pedro de Heredia → Blas de Lezo → El Socorro → El Campestre",
            "El Campestre (cerca de cancha principal del barrio)", "#1f77b4"),
        new RouteReference("CAM-CAS", "CAMPESTRE-CASTILLO", "COOINTRACAR",
            List.of("EL CAMPESTRE", "EL SOCORRO", "BOCAGRANDE", "CASTILLOGRANDE"),
            "El Campestre → El Socorro → Blas de Lezo → Av. Santander → Bocagrande → Castillogrande",
            "Castillogrande → Bocagrande → Av. Santander → Blas de Lezo → El Socorro → El Campestre",
            "No informado", "#2a6f97"),
        new RouteReference("SFE-CEN", "SAN FERNANDO-CENTRO", "COOINTRACAR",
            List.of("SAN FERNANDO", "URBANIZACION SIMON BOLIVAR", "EL SOCORRO", "CENTRO"),
            "San Fernando → Simón Bolívar → El Socorro → Av. Pedro de Heredia → Bazurto → Centro",
            "Centro → Bazurto → Av. Pedro de Heredia → El Socorro → Simón Bolívar → San Fernando",
            "No informado", "#2a9d8f"),
        new RouteReference("BLA-CEN", "BLAS DE LEZO-CENTRO", "COOINTRACAR",
            List.of("BLAS DE LEZO", "CENTRO"),
            "Blas de Lezo → Transversal 54 → Av. Crisanto Luque → Av. Pedro de Heredia → Bazurto → Centro",
            "Centro → Bazurto → Av. Pedro de Heredia → Av. Crisanto Luque → Transversal 54 → Blas de Lezo",
            "No informado", "#457b9d"),
        new RouteReference("BAY-CEN", "BAYUNCA-CENTRO", "COOINTRACAR",
            List.of("BAYUNCA", "LA BOQUILLA", "CENTRO"),
            "Bayunca → Vía al Mar → La Boquilla → Av. Santander → Las Tenazas → Centro",
            "Centro → Las Tenazas → Av. Santander → La Boquilla → Vía al Mar → Bayunca",
            "No informado", "#6a994e"),
        new RouteReference("CAR-CEN", "CARACOLES-CENTRO", "COOTRANSURB",
            List.of("LOS CARACOLES", "NUEVO BOSQUE", "CENTRO"),
            "Los Caracoles → Nuevo Bosque → Av. del Bosque → Av. Crisanto Luque → Av. Pedro de Heredia → Bazurto → Centro",
            "Centro → Bazurto → Av. Pedro de Heredia → Av. Crisanto Luque → Nuevo Bosque → Los Caracoles",
            "No informado", "#e76f51"),
        new RouteReference("CAR-NBO", "CARACOLES-NUEVO BOSQUE", "COOTRANSURB",
            List.of("LOS CARACOLES", "LA CAMPIÑA", "NUEVO BOSQUE"),
            "Los Caracoles → La Campiña → Nuevo Bosque (sector INEM)",
            "Nuevo Bosque → La Campiña → Los Caracoles",
            "No informado", "#f4a261"),
        new RouteReference("NBO-CAN", "NUEVO BOSQUE-CANAPOTE", "COOTRANSURB",
            List.of("NUEVO BOSQUE", "CANAPOTE"),
            "Nuevo Bosque → Av. Vélez → Canapote → Plaza de Canapote",
            "Plaza de Canapote → Av. Vélez → Nuevo Bosque",
            "No informado", "#8d6e63"),
        new RouteReference("AGU-CEN", "AGUAS PRIETAS-CENTRO", "COOTRANSURB",
            List.of("VILLAS DE ARANJUEZ", "NUEVO BOSQUE", "CENTRO"),
            "Aguas Prietas → Villas de Aranjuez → Nuevo Bosque → Av. Pedro de Heredia → Bazurto → Centro",
            "Centro → Bazurto → Av. Pedro de Heredia → Nuevo Bosque → Villas de Aranjuez → Aguas Prietas",
            "No informado", "#6d597a"),
        new RouteReference("POZ-CEN", "POZÓN-CENTRO", "COOTRANSURB",
            List.of("EL POZON", "CIUDADELA 2000", "CENTRO"),
            "El Pozón → Ciudadela 2000 → Av. Pedro de Heredia → Bazurto → Centro",
            "Centro → Bazurto → Av. Pedro de Heredia → Ciudadela 2000 → El Pozón",
            "No informado", "#bc6c25")
    );

    private final RutaRepository rutaRepository;
    private final BarrioService barrioService;

    public TpcRouteReferenceSeederService(RutaRepository rutaRepository, BarrioService barrioService) {
        this.rutaRepository = rutaRepository;
        this.barrioService = barrioService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        deactivateLegacyProvisionalRoutes();
        for (RouteReference route : ROUTES) {
            saveDirection(route, true);
            saveDirection(route, false);
        }
    }

    private void deactivateLegacyProvisionalRoutes() {
        for (String code : LEGACY_PROVISIONAL_CODES) {
            Optional<Ruta> legacyRoute = rutaRepository.findByCodigo(code);
            if (legacyRoute.isPresent()) {
                Ruta route = legacyRoute.get();
                if (route.getDescripcion() != null
                        && route.getDescripcion().startsWith("DATOS PROVISIONALES")) {
                    route.setEstado(Ruta.Estado.INACTIVA);
                    route.setVerificada(false);
                    List<String> barrios = normalizarBarriosCatastrales(route.getBarriosOrdenados());
                    route.setBarrios(new ArrayList<>(barrios));
                    route.setBarriosOrdenados(new ArrayList<>(barrios));
                    if (!barrios.isEmpty()) {
                        route.setOrigen(barrios.get(0));
                        route.setDestino(barrios.get(barrios.size() - 1));
                    } else {
                        route.setOrigen(null);
                        route.setDestino(null);
                    }
                    for (Parada parada : route.getParadas()) {
                        String barrioAnterior = parada.getBarrio();
                        String barrioCanonico = barrioService.obtenerNombreCanonico(barrioAnterior);
                        if (barrioCanonico.isBlank() && barrioAnterior != null && !barrioAnterior.isBlank()) {
                            log.warn("Se quitó el barrio no catastral '{}' de la parada '{}' en la ruta {}.",
                                barrioAnterior, parada.getNombre(), code);
                        }
                        parada.setBarrio(barrioCanonico.isBlank() ? null : barrioCanonico);
                    }
                    rutaRepository.save(route);
                }
            }
        }
    }

    private void saveDirection(RouteReference reference, boolean ida) {
        String direction = ida ? "IDA" : "VUELTA";
        String code = "TPC-" + reference.code() + "-" + direction;
        List<String> neighborhoods = new ArrayList<>(reference.neighborhoods().stream()
            .map(barrioService::obtenerNombreCanonico)
            .toList());
        if (neighborhoods.stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException("La ruta " + reference.code()
                + " contiene un nombre que no existe en la cartografía catastral.");
        }
        if (!ida) {
            java.util.Collections.reverse(neighborhoods);
        }

        Ruta route = rutaRepository.findByCodigo(code).orElseGet(Ruta::new);
        route.setCodigo(code);
        route.setNombre(reference.signboard() + " · " + direction);
        route.setOrigen(neighborhoods.get(0));
        route.setDestino(neighborhoods.get(neighborhoods.size() - 1));
        route.setEstado(Ruta.Estado.ACTIVA);
        route.setVerificada(true);
        route.setColorHex(reference.color());
        route.setBarrios(new ArrayList<>(neighborhoods));
        route.setBarriosOrdenados(new ArrayList<>(neighborhoods));
        route.setDescripcion("Cooperativa: " + reference.cooperative()
            + " | Letrero: " + reference.signboard()
            + " | Ida: " + reference.outbound()
            + " | Vuelta: " + reference.inbound()
            + " | Punto de despacho: " + reference.dispatch()
            + " | " + SOURCE_NOTE);
        rutaRepository.save(route);
    }

    private List<String> normalizarBarriosCatastrales(List<String> barrios) {
        if (barrios == null) {
            return List.of();
        }
        List<String> nombresCanonicos = new ArrayList<>();
        for (String barrio : barrios) {
            String nombreCanonico = barrioService.obtenerNombreCanonico(barrio);
            if (nombreCanonico.isBlank()) {
                log.warn("Se omitió el nombre no catastral '{}' de una ruta provisional.", barrio);
            } else {
                nombresCanonicos.add(nombreCanonico);
            }
        }
        return nombresCanonicos;
    }

    private record RouteReference(String code, String signboard, String cooperative,
                                  List<String> neighborhoods, String outbound, String inbound,
                                  String dispatch, String color) {
    }
}
