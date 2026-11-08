package com.proaula.aula.Service;

import com.proaula.aula.dto.BarrioContextoResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Service
public class BarrioContextoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BarrioContextoService.class);
    private static final Map<String, String> ALIASES = Map.of(
        "ESPA A", "ESPANA",
        "OLAYA ST RAFAEL NU EZ", "OLAYA ST RAFAEL NUNEZ");

    private final Map<String, BarrioContextoResponse> contextoPorBarrio;
    private final boolean disponible;

    public BarrioContextoService(
            @Value("classpath:data/Barrios_Oficiales_Distrito_de_Cartagena_de_Indias_20261107.xml")
            Resource archivoContexto) {
        Map<String, BarrioContextoResponse> datos;
        try {
            datos = cargar(archivoContexto);
        } catch (IOException | ParserConfigurationException | SAXException exception) {
            LOGGER.error("No se pudo cargar el contexto oficial de barrios.", exception);
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

    private static Map<String, BarrioContextoResponse> cargar(Resource recurso)
            throws IOException, ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document documento;
        try (InputStream contenido = recurso.getInputStream()) {
            documento = factory.newDocumentBuilder().parse(contenido);
        }

        Map<String, BarrioContextoResponse> datos = new HashMap<>();
        NodeList filas = documento.getElementsByTagName("row");
        for (int indice = 0; indice < filas.getLength(); indice++) {
            Node nodo = filas.item(indice);
            if (!(nodo instanceof Element fila)) {
                continue;
            }

            String nombre = texto(fila, "barrio");
            String clave = normalizarNombre(nombre);
            if (clave.isEmpty()) {
                throw new SAXException("La fila " + indice + " no tiene nombre de barrio.");
            }

            BigDecimal area = decimal(fila, "area_ha", nombre);
            Integer poblacion = entero(fila, "personas", nombre);
            BigDecimal densidad = area != null && area.signum() > 0 && poblacion != null
                ? BigDecimal.valueOf(poblacion).divide(area, 1, RoundingMode.HALF_UP)
                : null;
            BarrioContextoResponse contexto = new BarrioContextoResponse(
                nombre,
                texto(fila, "idbarrio"),
                texto(fila, "loc"),
                entero(fila, "ucg", nombre),
                area,
                poblacion,
                entero(fila, "hogar", nombre),
                entero(fila, "viviendas", nombre),
                densidad);

            if (datos.putIfAbsent(clave, contexto) != null) {
                throw new SAXException("Nombre de barrio duplicado en el archivo: " + nombre);
            }
        }
        if (datos.isEmpty()) {
            throw new SAXException("El archivo oficial no contiene barrios.");
        }
        return datos;
    }

    private static String texto(Element fila, String campo) {
        NodeList valores = fila.getElementsByTagName(campo);
        if (valores.getLength() == 0) {
            return null;
        }
        String valor = valores.item(0).getTextContent();
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static BigDecimal decimal(Element fila, String campo, String barrio) throws SAXException {
        String valor = texto(fila, campo);
        if (valor == null) {
            return null;
        }
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException exception) {
            throw new SAXException("Valor inválido en " + campo + " para " + barrio + ".", exception);
        }
    }

    private static Integer entero(Element fila, String campo, String barrio) throws SAXException {
        BigDecimal valor = decimal(fila, campo, barrio);
        if (valor == null) {
            return null;
        }
        try {
            return valor.intValueExact();
        } catch (ArithmeticException exception) {
            throw new SAXException("Valor no entero en " + campo + " para " + barrio + ".", exception);
        }
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
        return ALIASES.getOrDefault(clave, clave);
    }
}
