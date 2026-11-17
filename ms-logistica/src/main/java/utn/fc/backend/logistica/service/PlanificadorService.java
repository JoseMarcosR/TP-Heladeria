package utn.fc.backend.logistica.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import utn.fc.backend.logistica.client.CamioneroRemoto;
import utn.fc.backend.logistica.client.CamionerosClient;
import utn.fc.backend.logistica.client.Coordenada;
import utn.fc.backend.logistica.client.EstadoCamionero;
import utn.fc.backend.logistica.client.EstadoPedido;
import utn.fc.backend.logistica.client.PedidoRemoto;
import utn.fc.backend.logistica.client.PedidosClient;
import utn.fc.backend.logistica.client.RoutesClient;
import utn.fc.backend.logistica.client.RutaCalculada;
import utn.fc.backend.logistica.config.LogisticaProperties;
import utn.fc.backend.logistica.dto.ViajeResponse;
import utn.fc.backend.logistica.exception.ConflictoException;
import utn.fc.backend.logistica.exception.PlanificacionNoViableException;
import utn.fc.backend.logistica.exception.ServicioExternoException;
import utn.fc.backend.logistica.model.ParadaViaje;
import utn.fc.backend.logistica.model.Viaje;
import utn.fc.backend.logistica.repository.ViajeRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * FUN-03: planificación manual de una región con corte y rollback (RN-02, RN-03, RN-04, RN-05).
 * <p>
 * Los pedidos se evalúan en memoria en el orden estricto de la cola (sin reordenar: TSP fuera de alcance).
 * Un pedido que rompe la capacidad o las 10 horas "sufre rollback": nunca se toca en ms-pedidos, queda
 * PENDIENTE, y el viaje se cierra con los N-1 anteriores.
 * <p>
 * Un pedido cuya heladería no tiene coordenadas válidas (RN-05) se RECHAZA: se salta, no suma tachos ni tiempo,
 * no entra al viaje ni a la URL y no corta el armado. Al terminar (viaje confirmado o 422 por piso) se pide a
 * ms-pedidos que lo mande al final de la cola regional, para que no bloquee las próximas planificaciones.
 * <p>
 * {@link #planificar(Long)} NO es @Transactional a propósito: hace llamadas HTTP a ms-pedidos, ms-camioneros y
 * OSRM, que una transacción local no puede revertir (y mantener una conexión de base abierta durante esas
 * llamadas solo agregaría bloqueo). La consistencia entre servicios se resuelve con una compensación explícita.
 */
@Service
public class PlanificadorService {

    private static final Logger log = LoggerFactory.getLogger(PlanificadorService.class);

    private final CamionerosClient camionerosClient;
    private final PedidosClient pedidosClient;
    private final RoutesClient routesClient;
    private final ViajeRepository viajeRepository;
    private final LogisticaProperties.Planificacion config;
    private final Coordenada fabrica;

    public PlanificadorService(CamionerosClient camionerosClient,
                               PedidosClient pedidosClient,
                               RoutesClient routesClient,
                               ViajeRepository viajeRepository,
                               LogisticaProperties props) {
        this.camionerosClient = camionerosClient;
        this.pedidosClient = pedidosClient;
        this.routesClient = routesClient;
        this.viajeRepository = viajeRepository;
        this.config = props.planificacion();
        this.fabrica = new Coordenada(props.fabrica().latitud(), props.fabrica().longitud());
    }

    public ViajeResponse planificar(Long idRegion) {
        // 1) RN-04: primer camionero de la cola FIFO global. Sin camioneros => 409 y nada cambia.
        CamioneroRemoto camionero = camionerosClient.siguiente()
                .orElseThrow(() -> new ConflictoException(
                        "RN-04: no hay camioneros DISPONIBLES con contrato activo. No se modificó ningún pedido ni camionero"));
        if (camionero.camion() == null || camionero.camion().capacidadMaxTachos() == null) {
            throw new ServicioExternoException(
                    "ms-camioneros devolvió un camionero sin datos de camión/capacidad",
                    HttpStatus.BAD_GATEWAY);
        }
        int capacidad = camionero.camion().capacidadMaxTachos();

        // 2) Cola regional PENDIENTE, ya en orden FIFO.
        List<PedidoRemoto> cola = pedidosClient.obtenerCola(idRegion);

        // 3) Evaluación secuencial en memoria (todavía no se modifica nada en ningún servicio).
        List<PedidoRemoto> incluidos = new ArrayList<>();
        List<Coordenada> paradas = new ArrayList<>();
        List<Long> rechazados = new ArrayList<>();
        int acumulado = 0;
        long tiempoTotal = 0;
        String corte = null;

        for (PedidoRemoto pedido : cola) {
            // RN-05: sin coordenadas válidas no se puede rutear ni armar el enlace. El pedido se rechaza y se
            // sigue con el próximo: no cuenta para capacidad, tiempo ni piso, y no corta el viaje.
            Coordenada coordenada = coordenadaValida(pedido);
            if (coordenada == null) {
                log.warn("RN-05: pedido {} rechazado en la planificación de la región {}: su heladería no tiene "
                        + "coordenadas válidas. Queda PENDIENTE y se mandará al final de la cola", pedido.id(), idRegion);
                rechazados.add(pedido.id());
                continue;
            }

            int tachos = pedido.cantidadTachos();

            // RN-02: capacidad del freezer
            if (acumulado + tachos > capacidad) {
                corte = "RN-02: el pedido " + pedido.id() + " (" + tachos + " tachos) excede la capacidad del camión ("
                        + capacidad + ") con " + acumulado + " ya cargados";
                break;
            }

            // RN-03: tiempo total = conducción (OSRM) + descargas. Se hace UNA llamada a OSRM por pedido
            // candidato (con todas las paradas incluidas más la nueva): es simple y exacto, a costa de
            // latencia y de carga sobre el servidor OSRM (decisión del equipo).
            List<Coordenada> candidatas = new ArrayList<>(paradas);
            candidatas.add(coordenada);
            RutaCalculada ruta = routesClient.calcular(fabrica, candidatas, config.incluirVueltaAFabrica());
            long total = ruta.duracionSegundos() + descargaSegundos(candidatas.size());
            if (total > config.limiteViajeSegundos()) {
                corte = "RN-03: agregar el pedido " + pedido.id() + " llevaría el viaje a " + total
                        + " s (límite " + config.limiteViajeSegundos() + " s)";
                break;
            }

            incluidos.add(pedido);
            paradas.add(coordenada);
            acumulado += tachos;
            tiempoTotal = total;
        }

        // 4) Piso mínimo: si no se alcanza, no se toca el camionero ni los pedidos válidos. Solo se reubican al
        //    final de la cola los rechazados por RN-05 (best-effort), para no dejarlos bloqueando la cola.
        if (acumulado < config.pisoMinimoTachos()) {
            reencolarRechazados(rechazados);
            throw new PlanificacionNoViableException(
                    "No se alcanza el piso mínimo de " + config.pisoMinimoTachos() + " tachos (acumulado: "
                            + acumulado + ")" + (corte != null ? ". Corte: " + corte : "")
                            + (rechazados.isEmpty() ? "" : ". Pedidos rechazados por coordenadas inválidas (RN-05, "
                            + "enviados al final de la cola): " + rechazados)
                            + ". Los pedidos y el camionero quedan sin cambios");
        }

        // 5) Persistir el viaje (una transacción local por save) y luego impactar en los otros servicios.
        Viaje viaje = new Viaje(idRegion, camionero.id(), acumulado, (int) tiempoTotal,
                construirUrl(paradas, fabrica, config.incluirVueltaAFabrica()));
        int orden = 1;
        for (PedidoRemoto pedido : incluidos) {
            viaje.agregarParada(new ParadaViaje(pedido.id(), orden++, config.tiempoDescargaMin()));
        }
        viajeRepository.save(viaje);

        // Último estado que se INTENTÓ dejar en cada pedido; es lo que la compensación debe deshacer.
        Map<Long, EstadoPedido> tocados = new LinkedHashMap<>();
        boolean camioneroIntentado = false;
        try {
            for (PedidoRemoto pedido : incluidos) {
                // Se registra ANTES de llamar: ante un timeout la respuesta es ambigua (ms-pedidos pudo haber
                // aplicado el cambio igual), así que conviene intentar revertirlo. Si en realidad no se aplicó,
                // la reversión responde 409 y solo queda registrada en el log.
                tocados.put(pedido.id(), EstadoPedido.ASIGNADO);
                pedidosClient.cambiarEstado(pedido.id(), EstadoPedido.ASIGNADO);
            }
            for (PedidoRemoto pedido : incluidos) {
                // Acá se registra DESPUÉS: si falla, el pedido sigue (al menos) ASIGNADO y se revierte como tal.
                pedidosClient.cambiarEstado(pedido.id(), EstadoPedido.EN_TRANSITO);
                tocados.put(pedido.id(), EstadoPedido.EN_TRANSITO);
            }
            camioneroIntentado = true;
            camionerosClient.cambiarEstado(camionero.id(), EstadoCamionero.EN_VIAJE);
        } catch (RuntimeException e) {
            log.error("Falla remota al confirmar el viaje de la región {}; se compensa", idRegion, e);
            compensar(viaje, tocados, camionero.id(), camioneroIntentado);
            // Los rechazados por RN-05 no se reencolan acá: la planificación se revirtió completa y un reintento
            // los volverá a evaluar (y a rechazar) sin dejar ningún estado a medias.
            if (e instanceof ServicioExternoException se) {
                throw new ServicioExternoException(
                        "Planificación revertida (el viaje no se confirmó): " + se.getMessage(), se.getStatus(), se);
            }
            throw e;
        }

        // 6) Viaje confirmado: recién ahora se mandan al final de la cola los rechazados por RN-05 (best-effort).
        reencolarRechazados(rechazados);
        return ViajeResponse.from(viaje);
    }

    /**
     * Pide a ms-pedidos que mande cada pedido rechazado por RN-05 al final de su cola regional. Es best-effort:
     * si falla, se registra un warn y se sigue (el pedido queda PENDIENTE en su lugar y se volverá a rechazar en la
     * próxima planificación); nunca hace fallar la planificación.
     */
    private void reencolarRechazados(List<Long> rechazados) {
        for (Long idPedido : rechazados) {
            try {
                pedidosClient.reencolarAlFinal(idPedido);
            } catch (RuntimeException ex) {
                log.warn("No se pudo mandar al final de la cola el pedido {} (rechazado por RN-05): {}",
                        idPedido, ex.getMessage());
            }
        }
    }

    /**
     * Compensación best-effort: cada paso se intenta aunque falle otro, y los errores se registran (no se relanzan,
     * para no tapar el error original). Si una reversión falla, el estado queda inconsistente y debe corregirse a
     * mano a partir del log. Transiciones usadas (las únicas "hacia atrás" que ms-pedidos acepta):
     * <ul>
     *   <li>ASIGNADO -> PENDIENTE (rollback de planificación).</li>
     *   <li>EN_TRANSITO -> NO_ENTREGADO, que ms-pedidos reencola como PENDIENTE en su posición original.</li>
     *   <li>Camionero EN_VIAJE -> DISPONIBLE (solo si se llegó a intentar ponerlo EN_VIAJE).</li>
     * </ul>
     * Por último se elimina el viaje local no confirmado.
     */
    private void compensar(Viaje viaje, Map<Long, EstadoPedido> tocados, Long idCamionero, boolean camioneroIntentado) {
        tocados.forEach((idPedido, estado) -> {
            EstadoPedido destino = estado == EstadoPedido.EN_TRANSITO ? EstadoPedido.NO_ENTREGADO : EstadoPedido.PENDIENTE;
            try {
                pedidosClient.cambiarEstado(idPedido, destino);
            } catch (RuntimeException ex) {
                log.error("No se pudo revertir el pedido {} a {}: {}", idPedido, destino, ex.getMessage());
            }
        });
        if (camioneroIntentado) {
            try {
                camionerosClient.cambiarEstado(idCamionero, EstadoCamionero.DISPONIBLE);
            } catch (RuntimeException ex) {
                log.error("No se pudo devolver el camionero {} a DISPONIBLE: {}", idCamionero, ex.getMessage());
            }
        }
        try {
            viajeRepository.delete(viaje);
        } catch (RuntimeException ex) {
            log.error("No se pudo descartar el viaje no confirmado: {}", ex.getMessage());
        }
    }

    /**
     * Descarga total en segundos según la lectura de RN-03 confirmada por el equipo:
     * <ul>
     *   <li>Por defecto ({@code descargaIncluyeUltimaParada=true}): N * tiempoDescargaMin ("20 min por cada parada").</li>
     *   <li>Alternativa ({@code false}, fórmula literal de RN-03): (N-1) * tiempoDescargaMin.</li>
     * </ul>
     */
    long descargaSegundos(int cantidadParadas) {
        int conDescarga = config.descargaIncluyeUltimaParada() ? cantidadParadas : Math.max(cantidadParadas - 1, 0);
        // *60L porque OSRM devuelve en minutos, entonces lo pasa a segundos
        return (long) conDescarga * config.tiempoDescargaMin() * 60L;
    }

    /**
     * RN-05: devuelve la coordenada de la heladería del pedido, o {@code null} si es inválida (sin heladería,
     * latitud/longitud nulas, fuera de rango o exactamente 0,0, que suele indicar un dato sin cargar).
     */
    static Coordenada coordenadaValida(PedidoRemoto pedido) {
        PedidoRemoto.HeladeriaRemota h = pedido.heladeria();
        if (h == null || h.latitud() == null || h.longitud() == null) {
            return null;
        }
        double lat = h.latitud().doubleValue();
        double lng = h.longitud().doubleValue();
        boolean fueraDeRango = lat < -90 || lat > 90 || lng < -180 || lng > 180;
        boolean nula = h.latitud().compareTo(BigDecimal.ZERO) == 0 && h.longitud().compareTo(BigDecimal.ZERO) == 0;
        if (fueraDeRango || nula) {
            return null;
        }
        return new Coordenada(lat, lng);
    }

    /** RN-05: https://www.google.com/maps/dir//lat,lng/lat,lng/... (origen vacío => "Tu ubicación"). */
    static String construirUrl(List<Coordenada> paradas, Coordenada fabrica, boolean incluirVuelta) {
        StringBuilder url = new StringBuilder("https://www.google.com/maps/dir//");
        List<Coordenada> recorrido = new ArrayList<>(paradas);
        if (incluirVuelta) {
            recorrido.add(fabrica);
        }
        for (int i = 0; i < recorrido.size(); i++) {
            if (i > 0) {
                url.append('/');
            }
            Coordenada c = recorrido.get(i);
            url.append(String.format(Locale.ROOT, "%.6f,%.6f", c.latitud(), c.longitud()));
        }
        return url.toString();
    }
}
