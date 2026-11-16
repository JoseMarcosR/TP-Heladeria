package utn.fc.backend.pedidos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utn.fc.backend.pedidos.dto.PedidoCreateRequest;
import utn.fc.backend.pedidos.dto.PedidoResponse;
import utn.fc.backend.pedidos.exception.ConflictoException;
import utn.fc.backend.pedidos.exception.RecursoNoEncontradoException;
import utn.fc.backend.pedidos.exception.ReglaNegocioException;
import utn.fc.backend.pedidos.model.Ciudad;
import utn.fc.backend.pedidos.model.EstadoPedido;
import utn.fc.backend.pedidos.model.Heladeria;
import utn.fc.backend.pedidos.model.Pedido;
import utn.fc.backend.pedidos.model.Region;
import utn.fc.backend.pedidos.repository.HeladeriaRepository;
import utn.fc.backend.pedidos.repository.PedidoRepository;
import utn.fc.backend.pedidos.repository.RegionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de PedidoService con Mockito (repositorios mockeados, sin Spring ni base de datos).
 * Cubren el alta de pedidos (RN-01), la cola regional y la máquina de estados del pedido.
 */
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    /** Repositorios simulados: se programan con when(...) en cada test. */
    @Mock
    PedidoRepository pedidoRepository;
    @Mock
    HeladeriaRepository heladeriaRepository;
    @Mock
    RegionRepository regionRepository;

    /** Servicio bajo prueba, con los mocks inyectados. */
    @InjectMocks
    PedidoService service;

    /** Crea una heladería de prueba en Villa María (región ESTE). */
    private static Heladeria heladeria() {
        Region region = new Region("ESTE");
        Ciudad ciudad = new Ciudad(region, "Villa María");
        return new Heladeria(ciudad, "Heladería Test", "Calle 1", new BigDecimal("-32.407"), new BigDecimal("-63.240"));
    }

    /** Crea un pedido de 60 tachos ya posicionado en el estado indicado. */
    private static Pedido pedidoEn(EstadoPedido estado) {
        Pedido pedido = new Pedido(heladeria(), 60);
        pedido.setEstado(estado);
        return pedido;
    }

    // ---------- RN-01 / alta ----------

    /** Alta válida: el pedido se guarda en estado PENDIENTE con la cantidad pedida. */
    @Test
    void crearPedidoValidoQuedaPendiente() {
        when(heladeriaRepository.findById(1L)).thenReturn(Optional.of(heladeria()));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        PedidoResponse respuesta = service.crear(new PedidoCreateRequest(1L, 60));

        assertEquals(EstadoPedido.PENDIENTE, respuesta.estado());
        assertEquals(60, respuesta.cantidadTachos());
    }

    /** Cantidades fuera del rango permitido (RN-01): lanza ReglaNegocioException y no guarda. */
    @ParameterizedTest
    @CsvSource({"30", "39", "81", "100"})
    void crearPedidoFueraDeRangoSeRechaza(int tachos) {
        assertThrows(ReglaNegocioException.class, () -> service.crear(new PedidoCreateRequest(1L, tachos)));
        verify(pedidoRepository, never()).save(any());
    }

    /** Heladería inexistente: lanza RecursoNoEncontradoException. */
    @Test
    void crearPedidoConHeladeriaInexistenteFalla() {
        when(heladeriaRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.crear(new PedidoCreateRequest(99L, 50)));
    }

    // ---------- cola regional ----------

    /** Pedir la cola de una región inexistente: lanza RecursoNoEncontradoException. */
    @Test
    void colaDeRegionInexistenteFalla() {
        when(regionRepository.existsById(9L)).thenReturn(false);
        assertThrows(RecursoNoEncontradoException.class, () -> service.listarCola(9L));
    }

    /** La cola de una región existente devuelve sus pedidos PENDIENTES. */
    @Test
    void colaDevuelveLosPedidosPendientesDeLaRegion() {
        when(regionRepository.existsById(1L)).thenReturn(true);
        when(pedidoRepository.findCola(1L, EstadoPedido.PENDIENTE)).thenReturn(List.of(pedidoEn(EstadoPedido.PENDIENTE)));

        assertEquals(1, service.listarCola(1L).size());
    }

    // ---------- transiciones de estado ----------

    /** Transiciones permitidas: el pedido queda en el estado esperado (NO_ENTREGADO se reencola como PENDIENTE). */
    @ParameterizedTest
    @CsvSource({
            "PENDIENTE,ASIGNADO,ASIGNADO",
            "ASIGNADO,EN_TRANSITO,EN_TRANSITO",
            "ASIGNADO,PENDIENTE,PENDIENTE",
            "EN_TRANSITO,ENTREGADO,ENTREGADO",
            // NO_ENTREGADO reencola: queda persistido como PENDIENTE
            "EN_TRANSITO,NO_ENTREGADO,PENDIENTE"
    })
    void transicionesValidas(EstadoPedido actual, EstadoPedido destino, EstadoPedido persistido) {
        Pedido pedido = pedidoEn(actual);
        when(pedidoRepository.findDetalleById(5L)).thenReturn(Optional.of(pedido));

        PedidoResponse respuesta = service.cambiarEstado(5L, destino);

        assertEquals(persistido, pedido.getEstado());
        assertEquals(persistido, respuesta.estado());
    }

    /** Transiciones no permitidas: lanzan ConflictoException y el estado no cambia. */
    @ParameterizedTest
    @CsvSource({
            "PENDIENTE,EN_TRANSITO",
            "PENDIENTE,ENTREGADO",
            "PENDIENTE,NO_ENTREGADO",
            "PENDIENTE,PENDIENTE",
            "ASIGNADO,ENTREGADO",
            "ASIGNADO,NO_ENTREGADO",
            "EN_TRANSITO,PENDIENTE",
            "EN_TRANSITO,ASIGNADO",
            "ENTREGADO,PENDIENTE",
            "ENTREGADO,ASIGNADO",
            "ENTREGADO,NO_ENTREGADO"
    })
    void transicionesInvalidasDanConflicto(EstadoPedido actual, EstadoPedido destino) {
        Pedido pedido = pedidoEn(actual);
        when(pedidoRepository.findDetalleById(5L)).thenReturn(Optional.of(pedido));

        assertThrows(ConflictoException.class, () -> service.cambiarEstado(5L, destino));
        assertEquals(actual, pedido.getEstado());
    }

    /** Cambiar estado de un pedido inexistente: lanza RecursoNoEncontradoException. */
    @Test
    void cambiarEstadoDePedidoInexistenteFalla() {
        when(pedidoRepository.findDetalleById(404L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.cambiarEstado(404L, EstadoPedido.ASIGNADO));
    }

    // ---------- reencolado al final (RN-05, uso interno de ms-logistica) ----------

    /** Reencolar un pedido PENDIENTE lo mantiene PENDIENTE, conserva fechaCreacion y actualiza ultimaActualizacionCola. */
    @Test
    void reencolarAlFinalMantienePendienteYMandaAlFondoDeLaCola() {
        Pedido pedido = pedidoEn(EstadoPedido.PENDIENTE);
        Instant creacionOriginal = pedido.getFechaCreacion();
        Instant antes = Instant.parse("2026-01-01T00:00:00Z");
        pedido.setUltimaActualizacionCola(antes);
        when(pedidoRepository.findDetalleById(10L)).thenReturn(Optional.of(pedido));

        PedidoResponse respuesta = service.reencolarAlFinal(10L);

        assertEquals(EstadoPedido.PENDIENTE, pedido.getEstado());
        assertEquals(EstadoPedido.PENDIENTE, respuesta.estado());
        assertEquals(creacionOriginal, pedido.getFechaCreacion());
        assertTrue(pedido.getUltimaActualizacionCola().isAfter(antes));
    }

    /** Reencolar un pedido que no está PENDIENTE lanza ConflictoException y no altera su posición en la cola. */
    @ParameterizedTest
    @CsvSource({"ASIGNADO", "EN_TRANSITO", "ENTREGADO"})
    void reencolarPedidoNoPendienteDaConflicto(EstadoPedido estadoActual) {
        Pedido pedido = pedidoEn(estadoActual);
        Instant posicionOriginal = Instant.parse("2026-01-01T00:00:00Z");
        pedido.setUltimaActualizacionCola(posicionOriginal);
        when(pedidoRepository.findDetalleById(10L)).thenReturn(Optional.of(pedido));

        assertThrows(ConflictoException.class, () -> service.reencolarAlFinal(10L));
        assertEquals(estadoActual, pedido.getEstado());
        assertEquals(posicionOriginal, pedido.getUltimaActualizacionCola());
    }

    /** Reencolar un pedido inexistente lanza RecursoNoEncontradoException. */
    @Test
    void reencolarPedidoInexistenteDa404() {
        when(pedidoRepository.findDetalleById(404L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.reencolarAlFinal(404L));
    }
}
