package utn.fc.backend.logistica.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Vista mínima del camionero devuelto por GET /v1/camioneros/siguiente (Tolerant Reader). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CamioneroRemoto(Long id, String nombreCompleto, String telefono, EstadoCamionero estado, CamionRemoto camion) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CamionRemoto(Long id, String patente, Integer capacidadMaxTachos) {
    }
}
