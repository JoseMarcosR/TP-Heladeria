package utn.fc.backend.camioneros.dto;

import jakarta.validation.constraints.NotNull;
import utn.fc.backend.camioneros.model.EstadoCamionero;

public record EstadoCamioneroRequest(
        @NotNull(message = "estado es obligatorio")
        EstadoCamionero estado
) {
}
