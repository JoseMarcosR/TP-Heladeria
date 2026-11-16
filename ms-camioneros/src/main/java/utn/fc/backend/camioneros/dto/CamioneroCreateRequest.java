package utn.fc.backend.camioneros.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** FUN-02: alta del binomio Camionero => Camión. contratoActivo es opcional (por defecto true). */
public record CamioneroCreateRequest(
        @NotBlank(message = "dni es obligatorio")
        @Size(max = 20, message = "dni admite hasta 20 caracteres")
        String dni,

        @NotBlank(message = "nombreCompleto es obligatorio")
        @Size(max = 150, message = "nombreCompleto admite hasta 150 caracteres")
        String nombreCompleto,

        @NotBlank(message = "telefono es obligatorio")
        @Size(max = 30, message = "telefono admite hasta 30 caracteres")
        String telefono,

        Boolean contratoActivo,

        @NotNull(message = "camion es obligatorio")
        @Valid
        CamionRequest camion
) {
}
