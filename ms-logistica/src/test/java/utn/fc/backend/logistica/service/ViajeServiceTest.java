package utn.fc.backend.logistica.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import utn.fc.backend.logistica.client.CamionerosClient;
import utn.fc.backend.logistica.client.EstadoCamionero;
import utn.fc.backend.logistica.client.EstadoPedido;
import utn.fc.backend.logistica.client.PedidosClient;
import utn.fc.backend.logistica.dto.CierreViajeRequest;
import utn.fc.backend.logistica.dto.CierreViajeRequest.ItemCierre;
import utn.fc.backend.logistica.dto.ResultadoEntrega;
import utn.fc.backend.logistica.dto.ViajeResponse;
import utn.fc.backend.logistica.exception.ConflictoException;
import utn.fc.backend.logistica.exception.RecursoNoEncontradoException;
import utn.fc.backend.logistica.exception.ReglaNegocioException;
import utn.fc.backend.logistica.exception.ServicioExternoException;
import utn.fc.backend.logistica.model.EstadoViaje;
import utn.fc.backend.logistica.model.ParadaViaje;
import utn.fc.backend.logistica.model.Viaje;
import utn.fc.backend.logistica.repository.ViajeRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de ViajeService con Mockito (repositorio y clientes remotos mockeados).
 * Cubren el cierre de viajes: validaciones, actualización de pedidos/camionero y manejo de fallas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ViajeServiceTest {

    private static final long ID_VIAJE = 5L;
    private static final long ID_CAMIONERO = 7L;

    /** Dependencias simuladas: repositorio de viajes y clientes de ms-pedidos y ms-camioneros. */
    @Mock
    ViajeRepository viajeRepository;
    @Mock
    PedidosClient pedidosClient;
    @Mock
    CamionerosClient camionerosClient;

    /** Servicio bajo prueba y viaje de ejemplo (2 paradas: pedidos 10 y 11). */
    ViajeService service;
    Viaje viaje;

    /** Arma un viaje PLANIFICADO con dos paradas y lo devuelve el repositorio para ID_VIAJE. */
    @BeforeEach
    void setUp() {
        service = new ViajeService(viajeRepository, pedidosClient, camionerosClient);
        viaje = new Viaje(1L, ID_CAMIONERO, 120, 9_600, "https://www.google.com/maps/dir//-31.5,-64.1/-32.0,-63.0");
        viaje.agregarParada(new ParadaViaje(10L, 1, 20));
        viaje.agregarParada(new ParadaViaje(11L, 2, 20));
        when(viajeRepository.findDetalleById(ID_VIAJE)).thenReturn(Optional.of(viaje));
    }

    /** Arma el request de cierre con los resultados de entrega indicados. */
    private static CierreViajeRequest cierre(ItemCierre... items) {
        return new CierreViajeRequest(List.of(items));
    }

    /** Cierre válido: finaliza el viaje, actualiza pedidos, libera al camionero y guarda, en ese orden. */
    @Test
    void cierreConUnaEntregaYUnaNoEntregaReencolaYLiberaAlCamionero() {
        ViajeResponse r = service.cerrar(ID_VIAJE, cierre(
                new ItemCierre(10L, ResultadoEntrega.ENTREGADO),
                new ItemCierre(11L, ResultadoEntrega.NO_ENTREGADO)));

        assertEquals(EstadoViaje.FINALIZADO, r.estado());
        assertNotNull(r.fechaCierre());
        assertTrue(r.paradas().get(0).entregadoConfirmado());
        assertFalse(r.paradas().get(1).entregadoConfirmado());

        InOrder orden = inOrder(pedidosClient, camionerosClient, viajeRepository);
        orden.verify(pedidosClient).cambiarEstado(10L, EstadoPedido.ENTREGADO);
        orden.verify(pedidosClient).cambiarEstado(11L, EstadoPedido.NO_ENTREGADO); // ms-pedidos lo reencola como PENDIENTE
        orden.verify(camionerosClient).cambiarEstado(ID_CAMIONERO, EstadoCamionero.DISPONIBLE); // al final de la cola FIFO
        orden.verify(viajeRepository).save(viaje);
    }

    /** Cerrar un viaje ya FINALIZADO: ConflictoException sin llamadas remotas. */
    @Test
    void cerrarUnViajeFinalizadoDaConflicto() {
        viaje.finalizar(Instant.now());

        assertThrows(ConflictoException.class, () -> service.cerrar(ID_VIAJE, cierre(
                new ItemCierre(10L, ResultadoEntrega.ENTREGADO),
                new ItemCierre(11L, ResultadoEntrega.ENTREGADO))));

        verifyNoInteractions(pedidosClient, camionerosClient);
        verify(viajeRepository, never()).save(any());
    }

    /** Falta el resultado de una parada: ReglaNegocioException y el viaje sigue PLANIFICADO. */
    @Test
    void faltaInformarUnaParadaSeRechazaSinTocarNada() {
        assertThrows(ReglaNegocioException.class, () -> service.cerrar(ID_VIAJE, cierre(
                new ItemCierre(10L, ResultadoEntrega.ENTREGADO))));

        verifyNoInteractions(pedidosClient, camionerosClient);
        assertEquals(EstadoViaje.PLANIFICADO, viaje.getEstado());
    }

    /** Informar un pedido ajeno al viaje: ReglaNegocioException. */
    @Test
    void pedidoQueNoPerteneceAlViajeSeRechaza() {
        assertThrows(ReglaNegocioException.class, () -> service.cerrar(ID_VIAJE, cierre(
                new ItemCierre(10L, ResultadoEntrega.ENTREGADO),
                new ItemCierre(99L, ResultadoEntrega.ENTREGADO))));

        verifyNoInteractions(pedidosClient, camionerosClient);
    }

    /** Informar dos veces el mismo pedido: ReglaNegocioException. */
    @Test
    void pedidoInformadoDosVecesSeRechaza() {
        assertThrows(ReglaNegocioException.class, () -> service.cerrar(ID_VIAJE, cierre(
                new ItemCierre(10L, ResultadoEntrega.ENTREGADO),
                new ItemCierre(10L, ResultadoEntrega.NO_ENTREGADO),
                new ItemCierre(11L, ResultadoEntrega.ENTREGADO))));

        verifyNoInteractions(pedidosClient, camionerosClient);
    }

    /** Viaje inexistente: cerrar y obtener lanzan RecursoNoEncontradoException. */
    @Test
    void viajeInexistenteDa404() {
        when(viajeRepository.findDetalleById(404L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.cerrar(404L, cierre(
                new ItemCierre(10L, ResultadoEntrega.ENTREGADO))));
        assertThrows(RecursoNoEncontradoException.class, () -> service.obtener(404L));
    }

    /** Si falla ms-pedidos, el viaje no se finaliza ni se guarda y el camionero no se libera. */
    @Test
    void siFallaMsPedidosElViajeNoSeFinaliza() {
        doThrow(new ServicioExternoException("ms-pedidos caído", HttpStatus.SERVICE_UNAVAILABLE))
                .when(pedidosClient).cambiarEstado(10L, EstadoPedido.ENTREGADO);

        assertThrows(ServicioExternoException.class, () -> service.cerrar(ID_VIAJE, cierre(
                new ItemCierre(10L, ResultadoEntrega.ENTREGADO),
                new ItemCierre(11L, ResultadoEntrega.ENTREGADO))));

        assertEquals(EstadoViaje.PLANIFICADO, viaje.getEstado());
        verify(viajeRepository, never()).save(any());
        verify(camionerosClient, never()).cambiarEstado(any(), any());
    }
}
