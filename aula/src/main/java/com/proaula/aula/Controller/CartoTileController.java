package com.proaula.aula.Controller;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class CartoTileController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CartoTileController.class);
    private static final String CARTO_TILE_URL =
        "https://basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png";

    private final String apiKey;
    private final RestTemplate restTemplate;

    public CartoTileController(@Value("${carto.api-key:${CARTO_API_KEY:}}") String apiKey) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    @GetMapping(value = "/api/map/tiles", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> obtenerMosaico(
        @RequestParam int z,
        @RequestParam int x,
        @RequestParam int y
    ) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La clave de CARTO no está configurada");
        }

        if (z < 0 || z > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nivel de zoom fuera de rango");
        }
        int limiteCoordenadas = 1 << z;
        if (x < 0 || x >= limiteCoordenadas || y < 0 || y >= limiteCoordenadas) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coordenadas de mosaico fuera de rango");
        }

        URI uri = UriComponentsBuilder.fromUriString(CARTO_TILE_URL)
            .queryParam("key", apiKey)
            .buildAndExpand(z, x, y)
            .encode()
            .toUri();

        try {
            ResponseEntity<byte[]> respuesta = restTemplate.getForEntity(uri, byte[].class);
            if (respuesta.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CARTO devolvió un mosaico vacío");
            }
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(7))
                    .cachePublic()
                    .staleWhileRevalidate(java.time.Duration.ofDays(1)))
                .body(respuesta.getBody());
        } catch (RestClientResponseException exception) {
            LOGGER.warn("CARTO rechazó la solicitud de mosaico con estado HTTP {}", exception.getStatusCode());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "CARTO no pudo entregar el mosaico");
        } catch (ResourceAccessException exception) {
            LOGGER.warn("No se pudo establecer conexión con CARTO para solicitar un mosaico");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo conectar con CARTO");
        }
    }
}
