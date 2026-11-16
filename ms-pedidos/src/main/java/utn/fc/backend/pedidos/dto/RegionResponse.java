package utn.fc.backend.pedidos.dto;

import utn.fc.backend.pedidos.model.Region;

public record RegionResponse(Long id, String nombre) {

    public static RegionResponse from(Region region) {
        return new RegionResponse(region.getId(), region.getNombre());
    }
}
