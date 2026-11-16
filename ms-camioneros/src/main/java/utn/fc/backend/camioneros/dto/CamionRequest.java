package utn.fc.backend.camioneros.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CamionRequest(
        @NotBlank(message = "camion.patente es obligatoria")
        @Size(max = 15, message = "camion.patente admite hasta 15 caracteres")
        String patente,

        @NotNull(message = "camion.capacidadMaxTachos es obligatorio")
        @Min(value = 1, message = "camion.capacidadMaxTachos debe ser al menos 1")
        @Max(value = 500, message = "camion.capacidadMaxTachos no puede superar 500 tachos")
        Integer capacidadMaxTachos
) {
}
