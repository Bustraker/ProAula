package com.proaula.aula.Service;

import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Repository.RutaRepository;
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
    private static final String SOURCE_NOTE =
        "Fuente: listado compartido por el usuario para noviembre de 2026; no se validó de forma independiente con una fuente oficial.";
    private static final List<String> LEGACY_PROVISIONAL_CODES =
        List.of("RUT-CAMP-SOC-BCG-CG", "RUT-POZ-TORICES", "RUT-37-SIM-CENTRO", "RUT-30A-NB-CENTRO");

    private static final List<RouteReference> ROUTES = List.of(
        new RouteReference("CAM-CEN", "CAMPETRE-CENTRO", "COOINTRACAR",
            List.of("El Campestre", "El Socorro", "Bazurto", "Centro Histórico"),
            "Barrio El Campestre → El Socorro → Av. Blas de Lezo → Av. Pedro de Heredia → Bazurto → Centro Histórico (Plaza de los Mártires)",
            "Centro → Bazurto → Av. Pedro de Heredia → Blas de Lezo → El Socorro → El Campestre",
            "El Campestre (cerca de cancha principal del barrio)", "#1f77b4"),
        new RouteReference("CAM-CAS", "CAMPETRE-CASTILLO", "COOINTRACAR",
            List.of("El Campestre", "El Socorro", "Bocagrande", "Castillogrande"),
            "El Campestre → El Socorro → Blas de Lezo → Av. Santander → Bocagrande → Castillogrande",
            "Castillogrande → Bocagrande → Av. Santander → Blas de Lezo → El Socorro → El Campestre",
            "No informado", "#2a6f97"),
        new RouteReference("SFE-CEN", "SAN FERNANDO-CENTRO", "COOINTRACAR",
            List.of("San Fernando", "Simón Bolívar", "El Socorro", "Bazurto", "Centro"),
            "San Fernando → Simón Bolívar → El Socorro → Av. Pedro de Heredia → Bazurto → Centro",
            "Centro → Bazurto → Av. Pedro de Heredia → El Socorro → Simón Bolívar → San Fernando",
            "No informado", "#2a9d8f"),
        new RouteReference("BLA-CEN", "BLAS DE LEZO-CENTRO", "COOINTRACAR",
            List.of("Blas de Lezo", "Bazurto", "Centro"),
            "Blas de Lezo → Transversal 54 → Av. Crisanto Luque → Av. Pedro de Heredia → Bazurto → Centro",
            "Centro → Bazurto → Av. Pedro de Heredia → Av. Crisanto Luque → Transversal 54 → Blas de Lezo",
            "No informado", "#457b9d"),
        new RouteReference("BAY-CEN", "BAYUNCA-CENTRO", "COOINTRACAR",
            List.of("Bayunca", "La Boquilla", "Las Tenazas", "Centro"),
            "Bayunca → Vía al Mar → La Boquilla → Av. Santander → Las Tenazas → Centro",
            "Centro → Las Tenazas → Av. Santander → La Boquilla → Vía al Mar → Bayunca",
            "No informado", "#6a994e"),
        new RouteReference("CAR-CEN", "CARACOLES-CENTRO", "COOTRANSURB",
            List.of("Los Caracoles", "Nuevo Bosque", "Bazurto", "Centro"),
            "Los Caracoles → Nuevo Bosque → Av. del Bosque → Av. Crisanto Luque → Av. Pedro de Heredia → Bazurto → Centro",
            "Centro → Bazurto → Av. Pedro de Heredia → Av. Crisanto Luque → Nuevo Bosque → Los Caracoles",
            "No informado", "#e76f51"),
        new RouteReference("CAR-NBO", "CARACOLES-NUEVO BOSQUE", "COOTRANSURB",
            List.of("Los Caracoles", "La Campiña", "Nuevo Bosque"),
            "Los Caracoles → La Campiña → Nuevo Bosque (sector INEM)",
            "Nuevo Bosque → La Campiña → Los Caracoles",
            "No informado", "#f4a261"),
        new RouteReference("NBO-CAN", "NUEVO BOSQUE-CANAPOTE", "COOTRANSURB",
            List.of("Nuevo Bosque", "Canapote"),
            "Nuevo Bosque → Av. Vélez → Canapote → Plaza de Canapote",
            "Plaza de Canapote → Av. Vélez → Nuevo Bosque",
            "No informado", "#8d6e63"),
        new RouteReference("AGU-CEN", "AGUAS PRIETAS-CENTRO", "COOTRANSURB",
            List.of("Aguas Prietas", "Villas de Aranjuez", "Nuevo Bosque", "Bazurto", "Centro"),
            "Aguas Prietas → Villas de Aranjuez → Nuevo Bosque → Av. Pedro de Heredia → Bazurto → Centro",
            "Centro → Bazurto → Av. Pedro de Heredia → Nuevo Bosque → Villas de Aranjuez → Aguas Prietas",
            "No informado", "#6d597a"),
        new RouteReference("POZ-CEN", "POZÓN-CENTRO", "COOTRANSURB",
            List.of("El Pozón", "Ciudadela 2000", "Bazurto", "Centro"),
            "El Pozón → Ciudadela 2000 → Av. Pedro de Heredia → Bazurto → Centro",
            "Centro → Bazurto → Av. Pedro de Heredia → Ciudadela 2000 → El Pozón",
            "No informado", "#bc6c25")
    );

    private final RutaRepository rutaRepository;

    public TpcRouteReferenceSeederService(RutaRepository rutaRepository) {
        this.rutaRepository = rutaRepository;
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
                    rutaRepository.save(route);
                }
            }
        }
    }

    private void saveDirection(RouteReference reference, boolean ida) {
        String direction = ida ? "IDA" : "VUELTA";
        String code = "TPC-" + reference.code() + "-" + direction;
        List<String> neighborhoods = new ArrayList<>(reference.neighborhoods());
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

    private record RouteReference(String code, String signboard, String cooperative,
                                  List<String> neighborhoods, String outbound, String inbound,
                                  String dispatch, String color) {
    }
}
