package com.proaula.aula.Service;

import com.proaula.aula.Barrios.Localidades;
import com.proaula.aula.Entity.Barrio;
import com.proaula.aula.Repository.BarrioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class BarrioService {

    @Autowired
    private BarrioRepository barrioRepository;

    private static final Map<String, double[]> COORDENADAS_CARTAGENA = new HashMap<>();
    private static final Map<String, double[]> COORDENADAS_SHAPEFILE = new HashMap<>();
    private static final Set<String> LOCALIDAD_1_NORMALIZADA = new HashSet<>();
    private static final Set<String> LOCALIDAD_2_NORMALIZADA = new HashSet<>();
    private static final Set<String> LOCALIDAD_3_NORMALIZADA = new HashSet<>();

    static {
        cargarCoordenadasDesdeShapefile();
        addCoordenadas("centro", 10.4236, -75.5478);
        addCoordenadas("el centro", 10.4236, -75.5478);
        addCoordenadas("la matuna", 10.4214, -75.5489);
        addCoordenadas("pie de la popa", 10.4356, -75.5389);
        addCoordenadas("la popa", 10.4356, -75.5389);
        addCoordenadas("crespo", 10.4456, -75.5622);
        addCoordenadas("el cabrero", 10.4389, -75.5556);
        addCoordenadas("san diego", 10.4256, -75.5456);
        addCoordenadas("chambacu", 10.4089, -75.5422);
        addCoordenadas("manga", 10.4156, -75.5389);
        addCoordenadas("el prado", 10.4556, -75.5489);
        addCoordenadas("bocagrande", 10.4056, -75.5622);
        addCoordenadas("castillogrande", 10.4022, -75.5689);
        addCoordenadas("getsemani", 10.4189, -75.5489);
        addCoordenadas("marbella", 10.4322, -75.5556);
        addCoordenadas("terminal", 10.4122, -75.5489);
        addCoordenadas("terminal de transporte", 10.3756, -75.5489);
        addCoordenadas("bazurto", 10.4156, -75.5522);
        addCoordenadas("mercado", 10.4189, -75.5489);
        addCoordenadas("espana", 10.4256, -75.5522);
        addCoordenadas("villa olympica", 10.4489, -75.5489);
        addCoordenadas("la bodeguita", 10.4089, -75.5456);
        addCoordenadas("madre bernarda", 10.4456, -75.5422);
        addCoordenadas("delicias", 10.4389, -75.5489);
        addCoordenadas("los angeles", 10.4322, -75.5456);
        addCoordenadas("universidad tecnologia", 10.4456, -75.5556);
        addCoordenadas("barcelona", 10.4489, -75.5522);
        addCoordenadas("las americas", 10.4522, -75.5489);
        addCoordenadas("el laguito", 10.3971, -75.5581);
        addCoordenadas("los ejecutios", 10.4200, -75.5430);
        addCoordenadas("cuatro vientos", 10.4300, -75.5400);
        addCoordenadas("patio portal", 10.3950, -75.5550);
        addCoordenadas("lo amador", 10.3975, -75.5510);
        addCoordenadas("crisanto luque", 10.4220, -75.5510);
        addCoordenadas("crisantoluque", 10.4220, -75.5510);
        addCoordenadas("cuidad jardin", 10.4050, -75.5500);
        addCoordenadas("maria auxiliadora", 10.4300, -75.5600);

        addCoordenadas("boston", 10.4522, -75.5422);
        addCoordenadas("la esperanza", 10.4022, -75.5656);
        addCoordenadas("la quinta", 10.4489, -75.5356);
        addCoordenadas("chiquinquira", 10.4256, -75.5689);
        addCoordenadas("tesca", 10.4122, -75.5756);
        addCoordenadas("el pozon", 10.3756, -75.5489);
        addCoordenadas("la floresta", 10.4256, -75.5589);
        addCoordenadas("la victoria", 10.4189, -75.5822);
        addCoordenadas("el recreo", 10.4122, -75.5489);
        addCoordenadas("recreo", 10.4122, -75.5489);
        addCoordenadas("bosque", 10.4389, -75.5456);
        addCoordenadas("nuevo bosque", 10.4422, -75.5489);
        addCoordenadas("alcibia", 10.4360, -75.5580);
        addCoordenadas("la candelaria", 10.4000, -75.5720);
        addCoordenadas("la maria", 10.4450, -75.5650);
        addCoordenadas("olaya st. central", 10.4500, -75.5600);
        addCoordenadas("olaya st. rafael nunez", 10.4500, -75.5620);
        addCoordenadas("olaya st. ricaurte", 10.4520, -75.5640);
        addCoordenadas("olaya st. 11 de noviembre", 10.4550, -75.5600);
        addCoordenadas("olaya villa olimpica", 10.4510, -75.5590);
        addCoordenadas("republica del libano", 10.4470, -75.5600);
        addCoordenadas("fredonia", 10.4460, -75.5650);
        addCoordenadas("flor del campo", 10.4440, -75.5580);
        addCoordenadas("la india", 10.4430, -75.5590);
        addCoordenadas("nuevo paraiso", 10.4460, -75.5520);
        addCoordenadas("olaya st. la magdalena", 10.4490, -75.5620);
        addCoordenadas("olaya st. stella", 10.4485, -75.5630);
        addCoordenadas("olaya st. zarabanda", 10.4495, -75.5610);
        addCoordenadas("urbanizacion colombiaton", 10.4440, -75.5580);
        addCoordenadas("villa estrella", 10.4460, -75.5560);
        addCoordenadas("olaya st. la puntilla", 10.4470, -75.5590);
        addCoordenadas("olaya st. playa blanca", 10.4475, -75.5570);
        addCoordenadas("olaya st. progreso", 10.4450, -75.5600);
        addCoordenadas("chapacua", 10.4370, -75.5630);
        addCoordenadas("chipre", 10.4210, -75.5700);
        addCoordenadas("el gallo", 10.4360, -75.5580);
        addCoordenadas("la castellana", 10.4410, -75.5580);
        addCoordenadas("las gaviotas", 10.4440, -75.5590);
        addCoordenadas("las palmeras", 10.4450, -75.5600);
        addCoordenadas("los alpes", 10.4420, -75.5620);
        addCoordenadas("nuevo porvenir", 10.4460, -75.5600);
        addCoordenadas("republica de venezuela", 10.4450, -75.5640);
        addCoordenadas("san antonio", 10.4490, -75.5560);
        addCoordenadas("san jose obrero", 10.4500, -75.5580);
        addCoordenadas("13 de junio", 10.4470, -75.5620);
        addCoordenadas("viejo porvenir", 10.4475, -75.5640);
        addCoordenadas("arroyo grande", 10.4420, -75.5670);
        addCoordenadas("las europas", 10.4500, -75.5600);
        addCoordenadas("arroyo de las canoas", 10.4450, -75.5630);
        addCoordenadas("arroyo de piedra", 10.4440, -75.5660);
        addCoordenadas("punta canoa", 10.4510, -75.5570);
        addCoordenadas("pontezuela", 10.4470, -75.5570);
        addCoordenadas("manzanillo del mar", 10.4490, -75.5560);
        addCoordenadas("puerto rey", 10.4480, -75.5560);
        addCoordenadas("tierra baja", 10.4470, -75.5550);
        addCoordenadas("la boquilla", 10.4711, -75.5115);
        addCoordenadas("bayunca", 10.4500, -75.5200);
        addCoordenadas("palmarito", 10.4680, -75.5350);
        addCoordenadas("el consulado", 10.4460, -75.5610);

        addCoordenadas("nelson mandela", 10.3856, -75.5622);
        LOCALIDAD_1_NORMALIZADA.addAll(buildNormalizados(Localidades.LOCALIDAD_1));
        LOCALIDAD_2_NORMALIZADA.addAll(buildNormalizados(Localidades.LOCALIDAD_2));
        LOCALIDAD_3_NORMALIZADA.addAll(buildNormalizados(Localidades.LOCALIDAD_3));
        addCoordenadas("almirante colon", 10.4556, -75.5356);
        addCoordenadas("blas de lezo", 10.4489, -75.5322);
        addCoordenadas("la central", 10.4056, -75.5556);
        addCoordenadas("los corales", 10.3922, -75.5622);
        addCoordenadas("santa monica", 10.3989, -75.5689);
        addCoordenadas("la concepcion", 10.4022, -75.5722);
        addCoordenadas("san jose de los campanos", 10.3889, -75.5756);
        addCoordenadas("santa lucia", 10.3956, -75.5689);
        addCoordenadas("villa rosita", 10.4089, -75.5622);
        addCoordenadas("ciudadela 11 de nov", 10.3822, -75.5689);
        addCoordenadas("ciudadela 2000", 10.3789, -75.5722);
        addCoordenadas("jorge eliecer gaitan", 10.3856, -75.5656);
        addCoordenadas("la esmeralda i", 10.3922, -75.5756);
        addCoordenadas("la esmeralda ii", 10.3889, -75.5789);
        addCoordenadas("san fernando", 10.3956, -75.5722);
        addCoordenadas("la sierrita", 10.3789, -75.5656);
        addCoordenadas("los santanderes", 10.3822, -75.5622);
        addCoordenadas("maria cano", 10.3889, -75.5589);
        addCoordenadas("nazareno", 10.3956, -75.5656);
        addCoordenadas("nueva delhi", 10.3822, -75.5556);
        addCoordenadas("nueva jerusalen", 10.3789, -75.5589);
        addCoordenadas("villa fanny", 10.3856, -75.5722);
        addCoordenadas("villa hermosa", 10.3922, -75.5689);
        addCoordenadas("el reposo", 10.3989, -75.5756);
        addCoordenadas("jaime pardo leal", 10.3856, -75.5789);
        addCoordenadas("los jardines", 10.3922, -75.5722);
        addCoordenadas("luis carlos galan", 10.3889, -75.5756);
        addCoordenadas("vista hermosa", 10.3956, -75.5789);
        addCoordenadas("albornoz", 10.3860, -75.5670);
        addCoordenadas("antonio jose de sucre", 10.3865, -75.5650);
        addCoordenadas("arroz barato", 10.3940, -75.5720);
        addCoordenadas("bellavista", 10.3970, -75.5720);
        addCoordenadas("ceballos", 10.3900, -75.5660);
        addCoordenadas("el libertador", 10.3890, -75.5700);
        addCoordenadas("policarpa", 10.3895, -75.5690);
        addCoordenadas("puerta de hierro", 10.3920, -75.5660);
        addCoordenadas("santa clara", 10.4020, -75.5680);
        addCoordenadas("20 de julio sur", 10.3910, -75.5630);
        addCoordenadas("villa barraza", 10.3880, -75.5660);
        addCoordenadas("villa rosa", 10.3920, -75.5690);
        addCoordenadas("el campestre", 10.3970, -75.5640);
        addCoordenadas("el carmelo", 10.4000, -75.5650);
        addCoordenadas("el milagro", 10.3980, -75.5710);
        addCoordenadas("el socorro", 10.3930, -75.5670);
        addCoordenadas("anita", 10.4010, -75.5680);
        addCoordenadas("providencia", 10.3915, -75.5690);
        addCoordenadas("san pedro", 10.3940, -75.5690);
        addCoordenadas("ternera", 10.3990, -75.5660);
        addCoordenadas("alameda", 10.3975, -75.5650);
        addCoordenadas("camilo torres", 10.3930, -75.5675);
        addCoordenadas("cesar florez", 10.3925, -75.5680);
        addCoordenadas("rossedal", 10.3920, -75.5770);
        addCoordenadas("villa rubia", 10.3935, -75.5700);
        addCoordenadas("el educador", 10.3900, -75.5720);
        addCoordenadas("henequen", 10.3920, -75.5740);
        addCoordenadas("la consolata", 10.3940, -75.5730);
        addCoordenadas("san pedro martir", 10.3900, -75.5725);
        addCoordenadas("pasacaballo", 10.3700, -75.5400);
        addCoordenadas("membrillal", 10.3950, -75.5730);
    }

    private static void addCoordenadas(String nombre, double lat, double lng) {
        // Shapefile coords take priority — only write if no real coord loaded yet
        String key = normalizeStatic(nombre);
        if (!COORDENADAS_CARTAGENA.containsKey(key)) {
            COORDENADAS_CARTAGENA.put(key, new double[]{lat, lng});
        }
    }

    private static String normalizeStatic(String nombre) {
        if (nombre == null) {
            return null;
        }
        return Normalizer.normalize(nombre.trim().toLowerCase(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace("ñ", "n");
    }

    private static void cargarCoordenadasDesdeShapefile() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = BarrioService.class.getResourceAsStream("/data/barrios-shapefile.json")) {
            if (is == null) {
                return;
            }
            List<Map<String, Object>> entries = mapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> entry : entries) {
                String nombre = (String) entry.get("nombre");
                Object latObj = entry.get("lat");
                Object lngObj = entry.get("lng");
                if (nombre == null || latObj == null || lngObj == null) {
                    continue;
                }
                double lat = ((Number) latObj).doubleValue();
                double lng = ((Number) lngObj).doubleValue();
                String clave = normalizeStatic(nombre);
                COORDENADAS_SHAPEFILE.put(clave, new double[]{lat, lng});
                COORDENADAS_CARTAGENA.put(clave, new double[]{lat, lng});
            }
        } catch (IOException ex) {
            System.err.println("No se pudo cargar coords desde shapefile: " + ex.getMessage());
        }
    }

    private String normalize(String nombre) {
        return normalizeStatic(nombre);
    }

    private String obtenerLocalidad(String nombre) {
        String normalizado = normalize(nombre);
        if (LOCALIDAD_1_NORMALIZADA.contains(normalizado)) {
            return "Localidad 1";
        }
        if (LOCALIDAD_2_NORMALIZADA.contains(normalizado)) {
            return "Localidad 2";
        }
        if (LOCALIDAD_3_NORMALIZADA.contains(normalizado)) {
            return "Localidad 3";
        }
        return "Cartagena";
    }

    private static Set<String> buildNormalizados(List<String> lista) {
        return lista.stream().map(BarrioService::normalizeStatic).collect(Collectors.toSet());
    }

    public Optional<Barrio> obtenerPorNombre(String nombre) {
        String normalizado = normalize(nombre);
        return barrioRepository.findAll().stream()
            .filter(b -> normalize(b.getNombre()).equals(normalizado))
            .findFirst();
    }

    public List<Barrio> obtenerTodos() {
        return barrioRepository.findAll();
    }

    @Transactional
    public void inicializarBarrios() {
        List<Barrio> existentes = barrioRepository.findAll();
        Map<String, Barrio> existByNormalized = existentes.stream()
            .collect(Collectors.toMap(b -> normalize(b.getNombre()), b -> b, (first, second) -> first));

        List<Barrio> barriosParaGuardar = new ArrayList<>();
        Set<String> procesados = new HashSet<>();

        for (String barrioNombre : Localidades.obtenerTodosLosBarrios()) {
            String normalizado = normalize(barrioNombre);
            if (!procesados.add(normalizado)) {
                continue;
            }

            Barrio barrio = existByNormalized.getOrDefault(normalizado, new Barrio());
            barrio.setNombre(barrioNombre.trim());
            barrio.setLocalidad(obtenerLocalidad(barrioNombre));

            double[] coords = COORDENADAS_CARTAGENA.get(normalizado);
            if (coords != null) {
                barrio.setLatitud(coords[0]);
                barrio.setLongitud(coords[1]);
            } else {
                barrio.setLatitud(getFallbackLatitud(barrio.getLocalidad()));
                barrio.setLongitud(getFallbackLongitud(barrio.getLocalidad()));
            }

            barriosParaGuardar.add(barrio);
        }

        barrioRepository.saveAll(barriosParaGuardar);
    }

    private double getFallbackLatitud(String localidad) {
        switch (localidad) {
            case "Localidad 1":
                return 10.4200;
            case "Localidad 2":
                return 10.4380;
            case "Localidad 3":
                return 10.3900;
            default:
                return 10.4200;
        }
    }

    private double getFallbackLongitud(String localidad) {
        switch (localidad) {
            case "Localidad 1":
                return -75.5500;
            case "Localidad 2":
                return -75.5650;
            case "Localidad 3":
                return -75.5650;
            default:
                return -75.5500;
        }
    }

    public Map<String, double[]> obtenerCoordenadas(List<String> nombresBarrios) {
        Map<String, double[]> resultado = new HashMap<>();
        for (String nombre : nombresBarrios) {
            String normalizado = normalize(nombre);
            double[] coords = COORDENADAS_CARTAGENA.get(normalizado);
            if (coords != null) {
                resultado.put(nombre, coords);
            } else {
                resultado.put(nombre, new double[]{getFallbackLatitud(obtenerLocalidad(nombre)), getFallbackLongitud(obtenerLocalidad(nombre))});
            }
        }
        return resultado;
    }
}
