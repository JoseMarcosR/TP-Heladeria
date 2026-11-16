package utn.fc.backend.logistica.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import utn.fc.backend.logistica.config.LogisticaProperties;
import utn.fc.backend.logistica.exception.ServicioExternoException;

import java.time.Instant;

/** Obtiene (y cachea hasta su vencimiento) el token client-credentials del cliente "ms-logistica". */
@Component
public class KeycloakTokenProvider {

    private final RestClient rest;
    private final LogisticaProperties.Keycloak config;

    private String token;
    private Instant expira = Instant.EPOCH;

    public KeycloakTokenProvider(LogisticaProperties props) {
        this.config = props.keycloak();
        this.rest = RestClients.crear(null, props.servicios().connectTimeoutMs(), props.servicios().readTimeoutMs());
    }

    public synchronized String token() {
        if (token != null && Instant.now().isBefore(expira)) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        try {
            TokenResponse respuesta = rest.post()
                    .uri(config.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (respuesta == null || respuesta.accessToken() == null) {
                throw new ServicioExternoException("Keycloak no devolvió un access_token", HttpStatus.BAD_GATEWAY);
            }
            token = respuesta.accessToken();
            expira = Instant.now().plusSeconds(Math.max(respuesta.expiresIn() - 30, 1));
            return token;
        } catch (RestClientException e) {
            throw RestClients.mapear("Keycloak (token de servicio)", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") long expiresIn) {
    }
}
