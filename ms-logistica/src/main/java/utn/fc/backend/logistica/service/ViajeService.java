package utn.fc.backend.logistica.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.logistica.client.CamionerosClient;
import utn.fc.backend.logistica.client.EstadoCamionero;
import utn.fc.backend.logistica.client.EstadoPedido;
import utn.fc.backend.logistica.client.PedidosClient;
import utn.fc.backend.logistica.dto.CierreViajeRequest;
import utn.fc.backend.logistica.dto.ResultadoEntrega;
import utn.fc.backend.logistica.dto.ViajeResponse;
import utn.fc.backend.logistica.exception.ConflictoException;
import utn.fc.backend.logistica.exception.RecursoNoEncontradoException;
import utn.fc.backend.logistica.exception.ReglaNegocioException;
import utn.fc.backend.logistica.model.EstadoViaje;
import utn.fc.backend.logistica.model.ParadaViaje;
import utn.fc.backend.logistica.model.Viaje;
import utn.fc.backend.logistica.repository.ViajeRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ViajeService {

    private final ViajeRepository viajeRepository;
    private final PedidosClient pedidosClient;
    private final CamionerosClient camionerosClient;

    public ViajeService(ViajeRepository viajeRepository, PedidosClient pedidosClient, CamionerosClient camionerosClient) {
        this.viajeRepository = viajeRepository;
        this.pedidosClient = pedidosClient;
        this.camionerosClient = camionerosClient;
    }

    @Transactional(readOnly = true)
    public List<ViajeResponse> listar() {
        return viajeRepository.findAllByOrderByFechaPlanificacionDesc().stream()
                .map(ViajeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ViajeResponse obtener(Long id) {
        return ViajeResponse.from(buscar(id));
    }

    /**
     * FUN-04: cierre manual. Valida que el viaje esté PLANIFICADO y que se informen todas las paradas;
     * actualiza cada pedido en ms-pedidos (NO_ENTREGADO lo reencola), libera al camionero (vuelve al final de la
     * cola FIFO) y finaliza el viaje. No es @Transactional: hay llamadas HTTP; el estado local se guarda al final.
     * <p>
     * LIMITACIÓN CONOCIDA (decisión aceptada por el equipo): si ms-pedidos (o ms-camioneros) falla a mitad del
     * cierre, los pedidos ya actualizados (ENTREGADO / NO_ENTREGADO, este último ya reencolado como PENDIENTE) no se
     * pueden revertir porque no existe una transición válida hacia atrás. El viaje local sigue PLANIFICADO (se guarda
     * recién al final), así que reintentar el cierre dará 409 en esos pedidos y hay que resolverlo a mano.
     */
    public ViajeResponse cerrar(Long id, CierreViajeRequest request) {
        Viaje viaje = buscar(id);
        if (viaje.getEstado() == EstadoViaje.FINALIZADO) {
            throw new ConflictoException("El viaje " + id + " ya está FINALIZADO");
        }

        Map<Long, ResultadoEntrega> resultados = new LinkedHashMap<>();
        for (CierreViajeRequest.ItemCierre item : request.resultados()) {
            if (resultados.put(item.idPedido(), item.estado()) != null) {
                throw new ReglaNegocioException("El pedido " + item.idPedido() + " está informado más de una vez");
            }
        }
        Set<Long> esperados = new LinkedHashSet<>();
        viaje.getParadas().forEach(p -> esperados.add(p.getIdPedido()));
        if (!resultados.keySet().equals(esperados)) {
            Set<Long> faltantes = new LinkedHashSet<>(esperados);
            faltantes.removeAll(resultados.keySet());
            Set<Long> sobrantes = new LinkedHashSet<>(resultados.keySet());
            sobrantes.removeAll(esperados);
            throw new ReglaNegocioException("Deben informarse exactamente las paradas del viaje. Faltan: "
                    + faltantes + ". No pertenecen al viaje: " + sobrantes);
        }

        // Sin compensación a propósito: ver LIMITACIÓN CONOCIDA en el Javadoc.
        for (ParadaViaje parada : viaje.getParadas()) {
            ResultadoEntrega resultado = resultados.get(parada.getIdPedido());
            pedidosClient.cambiarEstado(parada.getIdPedido(),
                    resultado == ResultadoEntrega.ENTREGADO ? EstadoPedido.ENTREGADO : EstadoPedido.NO_ENTREGADO);
        }
        camionerosClient.cambiarEstado(viaje.getIdCamionero(), EstadoCamionero.DISPONIBLE);

        for (ParadaViaje parada : viaje.getParadas()) {
            parada.setEntregadoConfirmado(resultados.get(parada.getIdPedido()) == ResultadoEntrega.ENTREGADO);
        }
        viaje.finalizar(Instant.now());
        viajeRepository.save(viaje);
        return ViajeResponse.from(viaje);
    }

    private Viaje buscar(Long id) {
        return viajeRepository.findDetalleById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el viaje con id " + id));
    }
}
