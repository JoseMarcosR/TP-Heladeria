package utn.fc.backend.logistica.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** FUN-04: resultado de cada pedido (parada) del viaje, informado por el camionero fuera del sistema. */
public record CierreViajeRequest(
        @NotEmpty(message = "resultados es obligatorio y debe incluir todas las paradas del viaje")
        List<@Valid @NotNull(message = "resultados no admite elementos nulos") ItemCierre> resultados
) {

    public record ItemCierre(
            @NotNull(message = "idPedido es obligatorio")
            Long idPedido,

            @NotNull(message = "estado es obligatorio (ENTREGADO o NO_ENTREGADO)")
            ResultadoEntrega estado
    ) {
    }
}
