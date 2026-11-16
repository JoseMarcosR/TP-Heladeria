package utn.fc.backend.logistica.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import utn.fc.backend.logistica.config.LogisticaProperties;
import utn.fc.backend.logistica.exception.ServicioExternoException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * OSRM (Open Source Routing Machine), servicio "route": GET {url}/route/v1/driving/{lng,lat;lng,lat;...}.
 * No requiere API key ni tarjeta. Por defecto usa el servidor público de demostración
 * (https://router.project-osrm.org); se puede apuntar a otro con la variable de entorno OSRM_URL.
 * <p>
 * OSRM espera las coordenadas en orden <b>longitud,latitud</b> (al revés que Google) y devuelve duración en
 * segundos y distancia en metros, ya sumadas para toda la ruta.
 */
@Component
public class OsrmRoutesClient implements RoutesClient {

    private final RestClient rest;
    private final String baseUrl;

    public OsrmRoutesClient(LogisticaProperties props) {
        LogisticaProperties.Routes cfg = props.routes();
        this.baseUrl = cfg.url();
        this.rest = RestClients.crear(null, cfg.connectTimeoutMs(), cfg.readTimeoutMs());
    }

    @Override
    public RutaCalculada calcular(Coordenada origen, List<Coordenada> paradas, boolean volverAlOrigen) {
        if (paradas == null || paradas.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una parada");
        }
        URI uri = construirUri(baseUrl, origen, paradas, volverAlOrigen);

        OsrmResponse respuesta;
        try {
            respuesta = rest.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(OsrmResponse.class);
        } catch (RestClientException e) {
            throw RestClients.mapear("OSRM", e);
        }
        return aRuta(respuesta);
    }

    /**
     * Arma la URL del servicio route. Se pasa como {@link URI} ya construida (sin plantillas) para que Spring no
     * re-codifique las comas y los punto y coma de la lista de coordenadas.
     */
    static URI construirUri(String baseUrl, Coordenada origen, List<Coordenada> paradas, boolean volverAlOrigen) {
        List<Coordenada> puntos = new ArrayList<>();
        puntos.add(origen);
        puntos.addAll(paradas);
        if (volverAlOrigen) {
            puntos.add(origen);
        }
        String coordenadas = puntos.stream()
                .map(c -> String.format(Locale.ROOT, "%.6f,%.6f", c.longitud(), c.latitud()))
                .collect(Collectors.joining(";"));
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(base + "/route/v1/driving/" + coordenadas
                + "?overview=false&alternatives=false&steps=false");
    }

    /** Valida la respuesta de OSRM (code "Ok" y al menos una ruta) y la convierte a segundos y metros enteros. */
    static RutaCalculada aRuta(OsrmResponse respuesta) {
        if (respuesta == null || !"Ok".equals(respuesta.code()) || respuesta.routes() == null
                || respuesta.routes().isEmpty() || respuesta.routes().get(0).duration() == null) {
            String codigo = respuesta == null ? "sin respuesta" : String.valueOf(respuesta.code());
            throw new ServicioExternoException(
                    "OSRM no devolvió una ruta válida para las paradas indicadas (code: " + codigo + ")",
                    HttpStatus.BAD_GATEWAY);
        }
        OsrmResponse.Route ruta = respuesta.routes().get(0);
        long distancia = ruta.distance() == null ? 0L : Math.round(ruta.distance());
        return new RutaCalculada(Math.round(ruta.duration()), distancia);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OsrmResponse(String code, List<Route> routes) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Route(Double duration, Double distance) {
        }
    }
}
