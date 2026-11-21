package com.proaula.aula.Barrios;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Catálogo oficial de barrios por localidad del Distrito de Cartagena de Indias,
 * sincronizado con la cartografía catastral oficial (Shapefile / GeoJSON).
 */
public class Localidades {

    // Localidad 1: Histórica y del Caribe Norte
    public static final List<String> LOCALIDAD_1 = Collections.unmodifiableList(Arrays.asList(
        "14 De Febrero",
        "Alto Bosque",
        "Altos De San Isidro",
        "Amberes",
        "Ararca",
        "Archipielago San Bernardo",
        "Armenia",
        "Barrio Chino",
        "Baru",
        "Belén - La Bendición De Dios",
        "Bocachica",
        "Bocagrande",
        "Bosquecito",
        "Bruselas",
        "Buenos Aires",
        "Calamares",
        "Camaguey",
        "Canapote",
        "Caño Del Oro",
        "Castillogrande",
        "Centro",
        "Cerro Albornoz",
        "Cerro De La Popa",
        "Chambacu",
        "Ciudadela La Paz",
        "Crespo",
        "Daniel Lemaitre",
        "El Bosque",
        "El Cabrero",
        "El Carmen",
        "El Country",
        "El Laguito",
        "El Prado",
        "Escallon Villa",
        "España",
        "Espinal",
        "Getsemani",
        "Isla De León",
        "Isla Fuerte",
        "Isla Mucura",
        "Islas Del Rosario",
        "Jose Antonio Galan",
        "Juan Xxiii",
        "Junin",
        "La Campiña",
        "La Matuna",
        "La Paz",
        "La Troncal",
        "Las Brisas",
        "Las Delicias",
        "Lo Amador",
        "Loma Fresca",
        "Los Angeles",
        "Los Cerros",
        "Los Comuneros",
        "Los Ejecutivos",
        "Manga",
        "Marbella",
        "Martinez Martelo",
        "Mirador Del Nuevo Bosque",
        "Nariño",
        "Nueva Granada",
        "Nueve De Abril",
        "Nuevo Bosque",
        "Olaya Herrera (com 5)",
        "Olaya Herrera (com 6)",
        "Pablo Vi - I",
        "Pablo Vi - Ii",
        "Palestina",
        "Paraguay",
        "Paraiso Ii",
        "Pedro Salazar",
        "Petare",
        "Pie De La Popa",
        "Pie Del Cerro",
        "Piedra De Bolivar",
        "Puerto Bello",
        "Punta Arenas",
        "Republica De Chile",
        "Republica Del Caribe",
        "Rubi",
        "San Bernardo",
        "San Diego",
        "San Francisco",
        "San Isidro",
        "San Pedro Y Libertad",
        "Santa Ana",
        "Santa Maria",
        "Santana",
        "Siete De Agosto",
        "Tacarigua",
        "Tierra Bomba",
        "Torices",
        "Villa Sandra",
        "Villas De Aranjuez",
        "Zaragocilla"
    ));

    // Localidad 2: De la Virgen y Turística
    public static final List<String> LOCALIDAD_2 = Collections.unmodifiableList(Arrays.asList(
        "Alcibia",
        "Arroyo De Las Canoas",
        "Arroyo De Piedra",
        "Arroyo Grande",
        "Bayunca",
        "Boston",
        "Chapacua",
        "Chipre",
        "Chiquinquira",
        "Ciudad Bicentenario",
        "El Gallo",
        "El Pozon",
        "Flor Del Campo",
        "Fredonia",
        "La Boquilla",
        "La Candelaria",
        "La Castellana",
        "La Esperanza",
        "La Floresta",
        "La India",
        "La Maria",
        "La Quinta",
        "Las Europas",
        "Las Gaviotas",
        "Las Palmeras",
        "Los Alpes",
        "Manzanillo Del Mar",
        "Nuevo Paraiso",
        "Nuevo Porvenir",
        "Olaya St. Central",
        "Olaya St. La Magdalena",
        "Olaya St. La Puntilla",
        "Olaya St. Playa Blanca",
        "Olaya St. Progreso",
        "Olaya St. Rafael Nuñez",
        "Olaya St. Ricaurte",
        "Olaya St. Stella",
        "Olaya St. Zarabanda",
        "Olaya St.11 De Noviembre",
        "Olaya Villa Olimpica",
        "Pontezuela",
        "Puerto Rey",
        "Punta Canoa",
        "Republica De Venezuela",
        "Republica Del Libano",
        "San Antonio",
        "San Jose Obrero",
        "Tesca",
        "Tierra Baja",
        "Trece De Junio",
        "Urbanizacion Colombiaton",
        "Viejo Porvenir",
        "Villa Estrella",
        "Villas De La Candelaria",
        "Zapatero"
    ));

    // Localidad 3: Industrial y de la Bahía
    public static final List<String> LOCALIDAD_3 = Collections.unmodifiableList(Arrays.asList(
        "Alameda La Victoria",
        "Albornoz",
        "Almirante Colon",
        "Anita",
        "Antonio Jose De Sucre",
        "Arroz Barato",
        "Bellavista",
        "Blas De Lezo",
        "Camilo Torres",
        "Ceballos",
        "Cesar Florez",
        "Ciudadela 11 De Noviembre",
        "Ciudadela 2000",
        "El Campestre",
        "El Carmelo",
        "El Educador",
        "El Libertador",
        "El Milagro",
        "El Recreo",
        "El Reposo",
        "El Socorro",
        "Henequen",
        "Jaime Pardo Leal",
        "Jorge Eliecer Gaitan",
        "La Carolina",
        "La Central",
        "La Concepcion",
        "La Consolata",
        "La Esmeralda I",
        "La Esmeralda Ii",
        "La Florida",
        "La Sierrita",
        "La Victoria",
        "Leticia",
        "Los Caracoles",
        "Los Corales",
        "Los Jardines",
        "Los Santanderes",
        "Luis Carlos Galan",
        "Mamonal",
        "Manuela Vergara De Curi",
        "Maria Cano",
        "Membrillal",
        "Nazareno",
        "Nelson Mandela",
        "Nueva Delhi",
        "Nueva Jerusalen",
        "Pasacaballos",
        "Policarpa",
        "Providencia",
        "Puerta De Hierro",
        "Recreo",
        "Rossedal",
        "San Fernando",
        "San Jose De Los Campanos",
        "San Pedro",
        "San Pedro Martir",
        "Santa Clara",
        "Santa Lucia",
        "Santa Monica",
        "Sectores Unidos",
        "Ternera",
        "Urbanizacion Simon Bolivar",
        "Veinte De Julio Sur",
        "Villa Barraza",
        "Villa Fanny",
        "Villa Hermosa",
        "Villa Rosa",
        "Villa Rosita",
        "Villa Rubia",
        "Vista Hermosa",
        "Zona Industrial"
    ));

    /**
     * Obtiene todos los barrios oficiales de la cartografía catastral de Cartagena
     * sin duplicados y manteniendo un orden determinista.
     */
    public static List<String> obtenerTodosLosBarrios() {
        Set<String> vistos = new LinkedHashSet<>();
        List<String> todos = new ArrayList<>();

        agregarSinDuplicados(todos, vistos, LOCALIDAD_1);
        agregarSinDuplicados(todos, vistos, LOCALIDAD_2);
        agregarSinDuplicados(todos, vistos, LOCALIDAD_3);

        return todos;
    }

    private static void agregarSinDuplicados(List<String> destino, Set<String> vistos, List<String> origen) {
        if (origen == null) {
            return;
        }
        for (String barrio : origen) {
            String valor = barrio == null ? null : barrio.trim();
            if (valor == null || valor.isEmpty()) {
                continue;
            }

            String clave = normalizarClave(valor);
            if (!vistos.add(clave)) {
                continue;
            }

            destino.add(valor);
        }
    }

    private static String normalizarClave(String valor) {
        if (valor == null) {
            return "";
        }
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace("ñ", "n")
            .replace("Ñ", "N")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .replaceAll("\\s+", " ")
            .trim()
            .toLowerCase(Locale.ROOT);
    }
}
