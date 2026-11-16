package utn.fc.backend.camioneros.dto;

import utn.fc.backend.camioneros.model.Camionero;
import utn.fc.backend.camioneros.model.EstadoCamionero;

import java.time.Instant;

public record CamioneroResponse(
        Long id,
        String dni,
        String nombreCompleto,
        String telefono,
        boolean contratoActivo,
        EstadoCamionero estado,
        Instant ultimaActualizacionCola,
        CamionResponse camion
) {

    public static CamioneroResponse from(Camionero c) {
        return new CamioneroResponse(
                c.getId(),
                c.getDni(),
                c.getNombreCompleto(),
                c.getTelefono(),
                c.isContratoActivo(),
                c.getEstado(),
                c.getUltimaActualizacionCola(),
                CamionResponse.from(c.getCamion()));
    }
}
