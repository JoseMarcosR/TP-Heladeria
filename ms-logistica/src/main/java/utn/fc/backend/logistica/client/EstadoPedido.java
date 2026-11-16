package utn.fc.backend.logistica.client;

/** Estados de ms-pedidos tal como los expone su API (copia local: sin librería común). */
public enum EstadoPedido {
    PENDIENTE,
    ASIGNADO,
    EN_TRANSITO,
    ENTREGADO,
    NO_ENTREGADO
}
