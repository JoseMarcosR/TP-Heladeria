package utn.fc.backend.pedidos.model;

/**
 * Estados del pedido según el contrato.
 * <p>
 * NO_ENTREGADO solo existe como valor de entrada (informe manual de cierre del viaje):
 * al recibirlo, el pedido se reencola y queda persistido como PENDIENTE.
 */
public enum EstadoPedido {
    PENDIENTE,
    ASIGNADO,
    EN_TRANSITO,
    ENTREGADO,
    NO_ENTREGADO
}
