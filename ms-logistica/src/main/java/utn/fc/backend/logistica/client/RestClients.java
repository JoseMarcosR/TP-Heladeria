package utn.fc.backend.logistica.client;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import utn.fc.backend.logistica.exception.ServicioExternoException;

import java.net.http.HttpClient;
import java.time.Duration;

/** Utilidades comunes de los clientes HTTP salientes. */
final class RestClients {

    private RestClients() {
    }

    static RestClient crear(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        // Cliente HTTP del JDK en HTTP/1.1 (HttpURLConnection falla de forma confusa ante 401 en PUT/POST con cuerpo)
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }
        return builder.build();
    }

    /** 4xx/5xx del servicio remoto => 502; sin conexión o timeout => 503. */
    static ServicioExternoException mapear(String servicio, RestClientException e) {
        if (e instanceof RestClientResponseException r) {
            String cuerpo = r.getResponseBodyAsString();
            if (cuerpo.length() > 300) {
                cuerpo = cuerpo.substring(0, 300) + "...";
            }
            return new ServicioExternoException(
                    servicio + " respondió HTTP " + r.getStatusCode().value() + ": " + cuerpo,
                    HttpStatus.BAD_GATEWAY, e);
        }
        if (e instanceof ResourceAccessException) {
            return new ServicioExternoException(
                    "No se pudo contactar a " + servicio + " (sin conexión o timeout)",
                    HttpStatus.SERVICE_UNAVAILABLE, e);
        }
        return new ServicioExternoException(
                "Error al invocar a " + servicio + ": " + e.getMessage(), HttpStatus.BAD_GATEWAY, e);
    }
}
