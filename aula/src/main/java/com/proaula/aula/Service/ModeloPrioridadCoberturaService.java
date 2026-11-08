package com.proaula.aula.Service;

import com.proaula.aula.Entity.Parada;
import com.proaula.aula.Entity.Ruta;
import com.proaula.aula.Repository.RutaRepository;
import com.proaula.aula.dto.BarrioContextoResponse;
import com.proaula.aula.dto.modelos.PrioridadCoberturaResponse;
import com.proaula.aula.dto.modelos.PrioridadCoberturaResponse.BarrioPrioridad;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ModeloPrioridadCoberturaService {
    private static final double PESO_POBLACION = 40.0;
    private static final double PUNTOS_SIN_RUTAS = 40.0;
    private static final double PUNTOS_UNA_RUTA = 20.0;
    private static final double PUNTOS_SIN_PARADAS_GEOREFERENCIADAS = 20.0;
    private static final double PUNTOS_PARADAS_SIN_COORDENADAS = 10.0;
    private static final int PRIORIDAD_ALTA_DESDE = 60;
    private static final int PRIORIDAD_MEDIA_DESDE = 35;

    private final BarrioContextoService barrioContextoService;
    private final RutaRepository rutaRepository;

    public ModeloPrioridadCoberturaService(
            BarrioContextoService barrioContextoService,
            RutaRepository rutaRepository) {
        this.barrioContextoService = barrioContextoService;
        this.rutaRepository = rutaRepository;
    }

    public PrioridadCoberturaResponse analizarCobertura(boolean datosSinteticosConfigurados) {
        Map<String, Cobertura> coberturaPorBarrio = construirCobertura();
        List<EntradaBarrio> barrios = barrioContextoService.obtenerTodos().stream()
            .map(contexto -> new EntradaBarrio(contexto, coberturaPorBarrio.getOrDefault(
                BarrioContextoService.normalizarNombre(contexto.nombreBarrio()), new Cobertura())))
            .toList();

        int poblacionMaxima = barrios.stream()
            .map(entrada -> entrada.contexto().poblacion() == null
                ? 0 : entrada.contexto().poblacion())
            .max(Integer::compareTo)
            .orElse(0);

        List<BarrioPrioridad> resultados = barrios.stream()
            .map(entrada -> puntuar(entrada, poblacionMaxima))
            .sorted((primero, segundo) -> {
                int porPrioridad = Double.compare(segundo.indicePrioridad(), primero.indicePrioridad());
                if (porPrioridad != 0) {
                    return porPrioridad;
                }
                int porPoblacion = Integer.compare(
                    segundo.poblacionRegistrada() == null ? 0 : segundo.poblacionRegistrada(),
                    primero.poblacionRegistrada() == null ? 0 : primero.poblacionRegistrada());
                return porPoblacion != 0 ? porPoblacion : primero.barrio().compareTo(segundo.barrio());
            })
            .toList();

        int altas = contarNivel(resultados, "ALTA");
        int medias = contarNivel(resultados, "MEDIA");
        int bajas = contarNivel(resultados, "BAJA");
        long poblacionSinRuta = resultados.stream()
            .filter(barrio -> barrio.rutasActivasVerificadas() == 0)
            .mapToLong(barrio -> barrio.poblacionRegistrada() == null
                ? 0 : barrio.poblacionRegistrada())
            .sum();

        return new PrioridadCoberturaResponse(
            "Prioridad potencial de revisión de cobertura",
            "1.0",
            "Índice explicable de 0 a 100: población relativa hasta 40 puntos, cero rutas verificadas 40 "
                + "o una ruta 20, y ausencia de paradas activas georreferenciadas hasta 20 puntos; "
                + "alta desde 60, media desde 35. No es una probabilidad ni una predicción de pasajeros.",
            "Barrios oficiales aportados y rutas/paradas activas verificadas registradas en Bustraker.",
            advertencia(datosSinteticosConfigurados),
            resultados.size(),
            altas,
            medias,
            bajas,
            poblacionSinRuta,
            resultados);
    }

    private Map<String, Cobertura> construirCobertura() {
        Map<String, Cobertura> resultado = new HashMap<>();
        for (Ruta ruta : rutaRepository.findAllByVerificadaTrue()) {
            if (!Boolean.TRUE.equals(ruta.getVerificada())
                    || ruta.getEstado() != Ruta.Estado.ACTIVA || !ruta.isOrdenBarriosConfirmado()) {
                continue;
            }

            Set<String> barriosRuta = new HashSet<>();
            for (String barrio : ruta.getBarriosOrdenados()) {
                String clave = BarrioContextoService.normalizarNombre(barrio);
                if (!clave.isEmpty()) {
                    barriosRuta.add(clave);
                }
            }
            for (String clave : barriosRuta) {
                resultado.computeIfAbsent(clave, ignorado -> new Cobertura())
                    .rutasActivasVerificadas++;
            }

            for (Parada parada : ruta.getParadas()) {
                if (parada.getEstado() != Parada.Estado.ACTIVA || parada.getBarrio() == null) {
                    continue;
                }
                String clave = BarrioContextoService.normalizarNombre(parada.getBarrio());
                if (!barriosRuta.contains(clave)) {
                    continue;
                }
                Cobertura cobertura = resultado.computeIfAbsent(clave, ignorado -> new Cobertura());
                if (parada.getId() != null) {
                    cobertura.paradasActivas.add(parada.getId());
                    if (tieneCoordenadasValidas(parada)) {
                        cobertura.paradasGeorreferenciadas.add(parada.getId());
                    }
                } else {
                    cobertura.paradasAnonimas++;
                    if (tieneCoordenadasValidas(parada)) {
                        cobertura.paradasGeorreferenciadasAnonimas++;
                    }
                }
            }
        }
        return resultado;
    }

    private BarrioPrioridad puntuar(EntradaBarrio entrada, int poblacionMaxima) {
        BarrioContextoResponse contexto = entrada.contexto();
        Cobertura cobertura = entrada.cobertura();
        double aportePoblacion = poblacionMaxima > 0 && contexto.poblacion() != null
            ? PESO_POBLACION * contexto.poblacion() / poblacionMaxima
            : 0.0;
        double aporteFaltaRuta = cobertura.rutasActivasVerificadas == 0
            ? PUNTOS_SIN_RUTAS
            : cobertura.rutasActivasVerificadas == 1 ? PUNTOS_UNA_RUTA : 0.0;
        int paradasActivas = cobertura.paradasActivas.size() + cobertura.paradasAnonimas;
        int paradasGeorreferenciadas = cobertura.paradasGeorreferenciadas.size()
            + cobertura.paradasGeorreferenciadasAnonimas;
        double aporteFaltaParadas = paradasGeorreferenciadas > 0
            ? 0.0
            : paradasActivas > 0
                ? PUNTOS_PARADAS_SIN_COORDENADAS
                : PUNTOS_SIN_PARADAS_GEOREFERENCIADAS;

        double indice = Math.min(100.0,
            aportePoblacion + aporteFaltaRuta + aporteFaltaParadas);
        List<String> factores = new ArrayList<>();
        factores.add(contexto.poblacion() == null
            ? "Sin población registrada en la fuente; aporte 0/40."
            : "Población registrada: " + contexto.poblacion() + " personas; aporte "
                + redondear(aportePoblacion) + "/40.");
        factores.add(cobertura.rutasActivasVerificadas == 0
            ? "Sin ruta activa verificada: +" + redondear(aporteFaltaRuta) + " puntos."
            : cobertura.rutasActivasVerificadas == 1
                ? "Una ruta activa verificada: +" + redondear(aporteFaltaRuta) + " puntos."
                : cobertura.rutasActivasVerificadas
                    + " rutas activas verificadas: +0 puntos por disponibilidad de rutas.");
        factores.add(paradasGeorreferenciadas > 0
            ? paradasGeorreferenciadas
                + " paradas activas tienen coordenadas registradas: +0 puntos por ubicación."
            : paradasActivas > 0
                ? "Hay paradas activas sin coordenadas: +" + redondear(aporteFaltaParadas)
                    + " puntos por ubicación."
                : "No hay paradas activas registradas: +" + redondear(aporteFaltaParadas)
                    + " puntos por ubicación.");

        return new BarrioPrioridad(
            contexto.nombreBarrio(),
            contexto.localidadOficial(),
            contexto.poblacion(),
            redondear(indice),
            nivel(indice),
            cobertura.rutasActivasVerificadas,
            paradasActivas,
            paradasGeorreferenciadas,
            redondear(aportePoblacion),
            redondear(aporteFaltaRuta),
            redondear(aporteFaltaParadas),
            List.copyOf(factores));
    }

    private static int contarNivel(List<BarrioPrioridad> barrios, String nivel) {
        return (int) barrios.stream()
            .filter(barrio -> nivel.equals(barrio.nivelPrioridad()))
            .count();
    }

    private static String nivel(double indice) {
        if (indice >= PRIORIDAD_ALTA_DESDE) {
            return "ALTA";
        }
        if (indice >= PRIORIDAD_MEDIA_DESDE) {
            return "MEDIA";
        }
        return "BAJA";
    }

    private static String advertencia(boolean datosSinteticosConfigurados) {
        String advertencia = "La población de la fuente no tiene año de medición identificado. "
            + "La cobertura se calcula con rutas y paradas registradas, no mide frecuencia, "
            + "demanda real ni calidad del servicio. Úselo para priorizar revisiones, no como "
            + "decisión automática de inversión.";
        return datosSinteticosConfigurados
            ? advertencia + " El entorno permite datos sintéticos; valide las rutas y paradas antes "
                + "de tomar decisiones."
            : advertencia;
    }

    private static boolean tieneCoordenadasValidas(Parada parada) {
        Double latitud = parada.getLatitud();
        Double longitud = parada.getLongitud();
        return latitud != null && longitud != null
            && Double.isFinite(latitud) && Double.isFinite(longitud)
            && latitud >= -90 && latitud <= 90
            && longitud >= -180 && longitud <= 180;
    }

    private static double redondear(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }

    private record EntradaBarrio(BarrioContextoResponse contexto, Cobertura cobertura) {
    }

    private static final class Cobertura {
        private int rutasActivasVerificadas;
        private int paradasAnonimas;
        private int paradasGeorreferenciadasAnonimas;
        private final Set<Long> paradasActivas = new HashSet<>();
        private final Set<Long> paradasGeorreferenciadas = new HashSet<>();
    }
}
