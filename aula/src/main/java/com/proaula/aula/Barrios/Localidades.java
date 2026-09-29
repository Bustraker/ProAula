package com.proaula.aula.Barrios;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Localidades {
    // Localidad 1
    public static final List<String> LOCALIDAD_1 = Arrays.asList(
        "bocagrande", "castillogrande", "centro", "chambacu", "crespo", "el cabrero",
        "el laguito", "getsemani", "la matuna", "manga", "marbella", "pie de la popa",
        "san diego", "los ejecutios", "cuatro vientos", "la bodeguita", "bazurto", 
        "mercado", "espana", "maria auxiliadora", "villa olympica", "el centro",
        "patio portal", "madre bernarda", "delicias", "lo amador", "el prado",
        "la popa", "terminal", "terminal de transporte", "crisanto luque", 
        "crisantoluque", "los angeles", "cuidad jardin", "universidad tecnologia",
        "Barcelona", "Las Americas"
    );

    // Localidad 2
    public static final List<String> LOCALIDAD_2 = Arrays.asList(
        "alcibia", "boston", "la candelaria", "la esperanza", "la maria", "la quinta",
        "chiquinquira", "olaya st. central", "olaya st. rafael nunez", 
        "olaya st. ricaurte", "olaya st. 11 de noviembre", "olaya villa olimpica", 
        "republica del libano", "tesca", "el pozon", "fredonia", "flor del campo", 
        "la india", "nuevo paraiso", "olaya st. la magdalena", "olaya st. stella", 
        "olaya st. zarabanda", "urbanizacion colombiaton", "villa estrella", 
        "olaya st. la puntilla", "olaya st. playa blanca", "olaya st. progreso", 
        "chapacua", "chipre", "el gallo", "la castellana", "la floresta", 
        "las gaviotas", "las palmeras", "los alpes", "nuevo porvenir", 
        "republica de venezuela", "san antonio", "san jose obrero", "13 de junio", 
        "viejo porvenir", "arroyo grande", "las europas", "arroyo de las canoas", 
        "arroyo de piedra", "punta canoa", "pontezuela", "manzanillo del mar", 
        "puerto rey", "tierra baja", "la boquilla", "bayunca", "palmarito", "el consulado"
    );

    // Localidad 3
    public static final List<String> LOCALIDAD_3 = Arrays.asList(
        "albornoz", "antonio jose de sucre", "arroz barato", "bellavista", "ceballos", 
        "el libertador", "policarpa", "puerta de hierro", "santa clara", 
        "20 de julio sur", "villa barraza", "villa rosa", "almirante colon", 
        "blas de lezo", "el campestre", "el carmelo", "el milagro", "el socorro", 
        "la central", "los caracoles", "los corales", "san pedro", "santa monica", 
        "anita", "el recreo", "la concepcion", "providencia", "san jose de los campanos", 
        "santa lucia", "ternera", "villa rosita", "alameda", "la victoria", 
        "camilo torres", "cesar florez", "ciudadela 11 de nov", "ciudadela 2000", 
        "jorge eliecer gaitan", "la esmeralda i", "la esmeralda ii", "san fernando", 
        "sectores unidos", "urbanizacion simon bolivar", "la florida", "la sierrita", 
        "los santanderes", "maria cano", "nazareno", "nelson mandela", "nueva delhi", 
        "nueva jerusalen", "rossedal", "villa fanny", "villa hermosa", "villa rubia", 
        "el educador", "el reposo", "henequen", "jaime pardo leal", "la consolata", 
        "la victoria", "los jardines", "luis carlos galan", "manuela vergara de curi", 
        "san pedro martir", "vista hermosa", "pasacaballo", "recreo", "membrillal", 
        "nuevo bosque", "bosque"
    );

    // Método para obtener todos los barrios
    public static List<String> obtenerTodosLosBarrios() {
        List<String> todos = new ArrayList<>();
        todos.addAll(LOCALIDAD_1);
        todos.addAll(LOCALIDAD_2);
        todos.addAll(LOCALIDAD_3);
        return todos;
    }
}