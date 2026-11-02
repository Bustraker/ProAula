package com.proaula.aula.Service;

import com.proaula.aula.dto.modelos.RecomendacionRutaRequest;
import com.proaula.aula.dto.modelos.RecomendacionRutaResponse;
import com.proaula.aula.dto.modelos.RiesgoRetrasoRequest;
import com.proaula.aula.dto.modelos.RiesgoRetrasoResponse;
import com.proaula.aula.dto.modelos.SugerenciasDestinoRequest;
import com.proaula.aula.dto.modelos.SugerenciasDestinoResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModelosPredictivosService {
    private final RestClient restClient;

    public ModelosPredictivosService(@Qualifier("modelosRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public RiesgoRetrasoResponse predecirRiesgo(RiesgoRetrasoRequest request) {
        return enviar("/v1/predictions/delay-risk", request, RiesgoRetrasoResponse.class);
    }

    public RecomendacionRutaResponse recomendarRuta(RecomendacionRutaRequest request) {
        return enviar("/v1/recommendations/routes", request, RecomendacionRutaResponse.class);
    }

    public SugerenciasDestinoResponse sugerirDestinos(SugerenciasDestinoRequest request) {
        return enviar("/v1/recommendations/destinations", request, SugerenciasDestinoResponse.class);
    }

    private <T> T enviar(String path, Object request, Class<T> responseType) {
        try {
            T response = restClient.post()
                .uri(path)
                .body(request)
                .retrieve()
                .body(responseType);
            if (response == null) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "El servicio de modelos devolvió una respuesta vacía.");
            }
            return response;
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio de modelos no está disponible.",
                exception);
        } catch (RestClientResponseException exception) {
            HttpStatus status = exception.getStatusCode().is4xxClientError()
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(
                status,
                "El servicio de modelos rechazó la solicitud.",
                exception);
        }
    }
}
