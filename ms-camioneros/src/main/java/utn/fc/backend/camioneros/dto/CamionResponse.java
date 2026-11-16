package utn.fc.backend.camioneros.dto;

import utn.fc.backend.camioneros.model.Camion;

public record CamionResponse(Long id, String patente, Integer capacidadMaxTachos, boolean activo) {

    public static CamionResponse from(Camion camion) {
        return new CamionResponse(camion.getId(), camion.getPatente(),
                camion.getCapacidadMaxTachos(), camion.isActivo());
    }
}
