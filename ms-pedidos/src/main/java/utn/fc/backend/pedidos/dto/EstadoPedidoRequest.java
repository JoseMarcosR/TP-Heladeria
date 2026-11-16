package utn.fc.backend.pedidos.dto;

import jakarta.validation.constraints.NotNull;
import utn.fc.backend.pedidos.model.EstadoPedido;

public record EstadoPedidoRequest(
        @NotNull(message = "estado es obligatorio")
        EstadoPedido estado
) {
}
