package utn.fc.backend.pedidos.dto;

import utn.fc.backend.pedidos.model.EstadoPedido;
import utn.fc.backend.pedidos.model.Heladeria;
import utn.fc.backend.pedidos.model.Pedido;

import java.math.BigDecimal;
import java.time.Instant;

/** Detalle del pedido con la ubicación de la heladería (la necesita ms-logistica para rutear). */
public record PedidoResponse(
        Long id,
        Integer cantidadTachos,
        EstadoPedido estado,
        Instant fechaCreacion,
        HeladeriaInfo heladeria
) {

    public record HeladeriaInfo(
            Long id,
            String nombre,
            String direccion,
            BigDecimal latitud,
            BigDecimal longitud,
            Long idCiudad,
            String ciudad,
            Long idRegion,
            String region
    ) {
    }

    public static PedidoResponse from(Pedido pedido) {
        Heladeria h = pedido.getHeladeria();
        HeladeriaInfo info = new HeladeriaInfo(
                h.getId(),
                h.getNombre(),
                h.getDireccion(),
                h.getLatitud(),
                h.getLongitud(),
                h.getCiudad().getId(),
                h.getCiudad().getNombre(),
                h.getCiudad().getRegion().getId(),
                h.getCiudad().getRegion().getNombre());
        return new PedidoResponse(
                pedido.getId(),
                pedido.getCantidadTachos(),
                pedido.getEstado(),
                pedido.getFechaCreacion(),
                info);
    }
}
