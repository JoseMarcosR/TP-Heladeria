package utn.fc.backend.logistica.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import utn.fc.backend.logistica.config.LogisticaProperties;

import java.util.Map;
import java.util.Optional;

@Component
public class CamionerosHttpClient implements CamionerosClient {

    private static final String SERVICIO = "ms-camioneros";

    private final RestClient rest;
    private final KeycloakTokenProvider tokens;

    public CamionerosHttpClient(LogisticaProperties props, KeycloakTokenProvider tokens) {
        this.tokens = tokens;
        this.rest = RestClients.crear(props.servicios().camionerosUrl(),
                props.servicios().connectTimeoutMs(), props.servicios().readTimeoutMs());
    }

    @Override
    public Optional<CamioneroRemoto> siguiente() {
        String bearer = tokens.token();
        try {
            CamioneroRemoto camionero = rest.get()
                    .uri("/v1/camioneros/siguiente")
                    .headers(h -> h.setBearerAuth(bearer))
                    .retrieve()
                    .body(CamioneroRemoto.class);
            return Optional.ofNullable(camionero);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw RestClients.mapear(SERVICIO, e);
        }
    }

    @Override
    public void cambiarEstado(Long idCamionero, EstadoCamionero estado) {
        String bearer = tokens.token();
        try {
            rest.put()
                    .uri("/v1/camioneros/{id}/estado", idCamionero)
                    .headers(h -> h.setBearerAuth(bearer))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("estado", estado.name()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw RestClients.mapear(SERVICIO + " (camionero " + idCamionero + " -> " + estado + ")", e);
        }
    }
}
