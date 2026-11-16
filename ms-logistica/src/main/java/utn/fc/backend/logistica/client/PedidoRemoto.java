package utn.fc.backend.logistica.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Vista mínima de un pedido de la cola regional de ms-pedidos (Tolerant Reader). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PedidoRemoto(Long id, Integer cantidadTachos, EstadoPedido estado, HeladeriaRemota heladeria) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeladeriaRemota(Long id, String nombre, String direccion, BigDecimal latitud, BigDecimal longitud) {
    }
}
