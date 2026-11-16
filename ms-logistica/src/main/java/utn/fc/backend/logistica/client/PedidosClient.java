package utn.fc.backend.logistica.client;

import java.util.List;

/** Acceso a ms-pedidos (solo endpoints permitidos a ServicioInterno). */
public interface PedidosClient {

    /** Cola regional PENDIENTE, en orden FIFO. Lanza RecursoNoEncontradoException si la región no existe. */
    List<PedidoRemoto> obtenerCola(Long idRegion);

    void cambiarEstado(Long idPedido, EstadoPedido estado);

    /**
     * Manda un pedido PENDIENTE al final de la cola de su región (endpoint interno PUT /v1/pedidos/{id}/reencolado).
     * Se usa para los pedidos rechazados por coordenadas inválidas (RN-05).
     */
    void reencolarAlFinal(Long idPedido);
}
