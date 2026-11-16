package utn.fc.backend.pedidos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** RN-01: cada pedido debe contener entre 40 y 80 tachos de 10 L. */
public record PedidoCreateRequest(
        @NotNull(message = "idHeladeria es obligatorio")
        Long idHeladeria,

        @NotNull(message = "cantidadTachos es obligatorio")
        @Min(value = 40, message = "RN-01: la cantidad de tachos debe estar entre 40 y 80")
        @Max(value = 80, message = "RN-01: la cantidad de tachos debe estar entre 40 y 80")
        Integer cantidadTachos
) {
}
