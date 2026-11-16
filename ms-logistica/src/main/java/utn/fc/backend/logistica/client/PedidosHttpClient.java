package utn.fc.backend.logistica.client;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import utn.fc.backend.logistica.config.LogisticaProperties;
import utn.fc.backend.logistica.exception.RecursoNoEncontradoException;

import java.util.List;
import java.util.Map;

@Component
public class PedidosHttpClient implements PedidosClient {

    private static final String SERVICIO = "ms-pedidos";

    private final RestClient rest;
    private final KeycloakTokenProvider tokens;

    public PedidosHttpClient(LogisticaProperties props, KeycloakTokenProvider tokens) {
        this.tokens = tokens;
        this.rest = RestClients.crear(props.servicios().pedidosUrl(),
                props.servicios().connectTimeoutMs(), props.servicios().readTimeoutMs());
    }

    @Override
    public List<PedidoRemoto> obtenerCola(Long idRegion) {
        String bearer = tokens.token();
        try {
            PedidoRemoto[] cola = rest.get()
                    .uri("/v1/pedidos/regiones/{idRegion}/cola", idRegion)
                    .headers(h -> h.setBearerAuth(bearer))
                    .retrieve()
                    .body(PedidoRemoto[].class);
            return cola == null ? List.of() : List.of(cola);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RecursoNoEncontradoException("No existe la región con id " + idRegion);
        } catch (RestClientException e) {
            throw RestClients.mapear(SERVICIO, e);
        }
    }

    @Override
    public void cambiarEstado(Long idPedido, EstadoPedido estado) {
        String bearer = tokens.token();
        try {
            rest.put()
                    .uri("/v1/pedidos/{id}/estado", idPedido)
                    .headers(h -> h.setBearerAuth(bearer))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("estado", estado.name()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw RestClients.mapear(SERVICIO + " (pedido " + idPedido + " -> " + estado + ")", e);
        }
    }

    @Override
    public void reencolarAlFinal(Long idPedido) {
        String bearer = tokens.token();
        try {
            rest.put()
                    .uri("/v1/pedidos/{id}/reencolado", idPedido)
                    .headers(h -> h.setBearerAuth(bearer))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw RestClients.mapear(SERVICIO + " (reencolado del pedido " + idPedido + ")", e);
        }
    }
}
