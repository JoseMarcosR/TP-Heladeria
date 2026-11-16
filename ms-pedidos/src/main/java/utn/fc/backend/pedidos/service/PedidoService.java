package utn.fc.backend.pedidos.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.pedidos.dto.PedidoCreateRequest;
import utn.fc.backend.pedidos.dto.PedidoResponse;
import utn.fc.backend.pedidos.dto.RegionResponse;
import utn.fc.backend.pedidos.exception.ConflictoException;
import utn.fc.backend.pedidos.exception.RecursoNoEncontradoException;
import utn.fc.backend.pedidos.exception.ReglaNegocioException;
import utn.fc.backend.pedidos.model.EstadoPedido;
import utn.fc.backend.pedidos.model.Heladeria;
import utn.fc.backend.pedidos.model.Pedido;
import utn.fc.backend.pedidos.repository.HeladeriaRepository;
import utn.fc.backend.pedidos.repository.PedidoRepository;
import utn.fc.backend.pedidos.repository.RegionRepository;

import java.time.Instant;
import java.util.List;

@Service
public class PedidoService {

    public static final int MIN_TACHOS = 40;
    public static final int MAX_TACHOS = 80;

    private final PedidoRepository pedidoRepository;
    private final HeladeriaRepository heladeriaRepository;
    private final RegionRepository regionRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         HeladeriaRepository heladeriaRepository,
                         RegionRepository regionRepository) {
        this.pedidoRepository = pedidoRepository;
        this.heladeriaRepository = heladeriaRepository;
        this.regionRepository = regionRepository;
    }

    /** FUN-01 / RN-01: registra el pedido en estado PENDIENTE y lo encola en la región de su heladería. */
    @Transactional
    public PedidoResponse crear(PedidoCreateRequest request) {
        int tachos = request.cantidadTachos();
        if (tachos < MIN_TACHOS || tachos > MAX_TACHOS) {
            // Defensa en profundidad: la misma regla ya se valida en el DTO.
            throw new ReglaNegocioException(
                    "RN-01: la cantidad de tachos debe estar entre " + MIN_TACHOS + " y " + MAX_TACHOS);
        }
        Heladeria heladeria = heladeriaRepository.findById(request.idHeladeria())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe la heladería con id " + request.idHeladeria()));
        Pedido pedido = pedidoRepository.save(new Pedido(heladeria, tachos));
        return PedidoResponse.from(pedido);
    }

    /** Cola regional: pedidos PENDIENTE de la región, ordenados por fecha de creación ascendente. */
    @Transactional(readOnly = true)
    public List<PedidoResponse> listarCola(Long idRegion) {
        if (!regionRepository.existsById(idRegion)) {
            throw new RecursoNoEncontradoException("No existe la región con id " + idRegion);
        }
        return pedidoRepository.findCola(idRegion, EstadoPedido.PENDIENTE).stream()
                .map(PedidoResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtener(Long id) {
        return PedidoResponse.from(buscar(id));
    }

    /**
     * Transiciones válidas:
     * PENDIENTE -> ASIGNADO; ASIGNADO -> EN_TRANSITO | PENDIENTE (rollback de planificación);
     * EN_TRANSITO -> ENTREGADO | NO_ENTREGADO. NO_ENTREGADO reencola: queda PENDIENTE
     * conservando su posición de cola original (no se toca ultimaActualizacionCola, vuelve a su lugar).
     */
    @Transactional
    public PedidoResponse cambiarEstado(Long id, EstadoPedido destino) {
        Pedido pedido = buscar(id);
        EstadoPedido actual = pedido.getEstado();
        if (!transicionPermitida(actual, destino)) {
            throw new ConflictoException(
                    "Transición de estado no permitida: " + actual + " -> " + destino);
        }
        pedido.setEstado(destino == EstadoPedido.NO_ENTREGADO ? EstadoPedido.PENDIENTE : destino);
        return PedidoResponse.from(pedido);
    }

    /**
     * Uso interno (ms-logistica, rol ServicioInterno): manda un pedido PENDIENTE al FINAL de la cola de su región.
     * <p>
     * Existe porque, al planificar, un pedido cuya heladería no tiene coordenadas válidas (RN-05) se rechaza y no
     * puede quedar primero en la cola bloqueando las siguientes planificaciones. No cambia el estado (sigue
     * PENDIENTE) ni la fecha de creación: solo refresca el criterio de orden de la cola. Si el pedido no está
     * PENDIENTE (por ejemplo, ya fue asignado por otra planificación) responde 409 y no lo toca.
     */
    @Transactional
    public PedidoResponse reencolarAlFinal(Long id) {
        Pedido pedido = buscar(id);
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new ConflictoException("Solo se puede reencolar un pedido PENDIENTE; el pedido " + id
                    + " está " + pedido.getEstado());
        }
        pedido.setUltimaActualizacionCola(Instant.now());
        return PedidoResponse.from(pedido);
    }

    @Transactional(readOnly = true)
    public List<RegionResponse> listarRegiones() {
        return regionRepository.findAll().stream().map(RegionResponse::from).toList();
    }

    static boolean transicionPermitida(EstadoPedido actual, EstadoPedido destino) {
        return switch (actual) {
            case PENDIENTE -> destino == EstadoPedido.ASIGNADO;
            case ASIGNADO -> destino == EstadoPedido.EN_TRANSITO || destino == EstadoPedido.PENDIENTE;
            case EN_TRANSITO -> destino == EstadoPedido.ENTREGADO || destino == EstadoPedido.NO_ENTREGADO;
            case ENTREGADO, NO_ENTREGADO -> false;
        };
    }

    private Pedido buscar(Long id) {
        return pedidoRepository.findDetalleById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido con id " + id));
    }
}
