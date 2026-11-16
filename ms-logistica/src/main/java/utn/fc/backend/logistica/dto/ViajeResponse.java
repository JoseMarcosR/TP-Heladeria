package utn.fc.backend.logistica.dto;

import utn.fc.backend.logistica.model.EstadoViaje;
import utn.fc.backend.logistica.model.ParadaViaje;
import utn.fc.backend.logistica.model.Viaje;

import java.time.Instant;
import java.util.List;

/** Hoja de ruta con sus paradas ordenadas y el enlace dinámico de Google Maps (RN-05). */
public record ViajeResponse(
        Long id,
        Long idRegion,
        Long idCamionero,
        Integer totalTachosCargados,
        Integer tiempoEstimadoSeg,
        String urlGoogleMaps,
        EstadoViaje estado,
        Instant fechaPlanificacion,
        Instant fechaCierre,
        List<ParadaResponse> paradas
) {

    public record ParadaResponse(
            Long id,
            Long idPedido,
            Integer ordenVisita,
            Integer tiempoDescargaMin,
            boolean entregadoConfirmado
    ) {
        static ParadaResponse from(ParadaViaje p) {
            return new ParadaResponse(p.getId(), p.getIdPedido(), p.getOrdenVisita(),
                    p.getTiempoDescargaMin(), p.isEntregadoConfirmado());
        }
    }

    public static ViajeResponse from(Viaje v) {
        List<ParadaResponse> paradas = v.getParadas().stream()
                .sorted((a, b) -> Integer.compare(a.getOrdenVisita(), b.getOrdenVisita()))
                .map(ParadaResponse::from)
                .toList();
        return new ViajeResponse(v.getId(), v.getIdRegion(), v.getIdCamionero(),
                v.getTotalTachosCargados(), v.getTiempoEstimadoSeg(), v.getUrlGoogleMaps(),
                v.getEstado(), v.getFechaPlanificacion(), v.getFechaCierre(), paradas);
    }
}
