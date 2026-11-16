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
import utn.fc.backend.logistica.exception.RecursoNoEncontradoException;
import utn.fc.backend.logistica.exception.ServicioExternoException;
import utn.fc.backend.logistica.model.EstadoViaje;
import utn.fc.backend.logistica.model.Viaje;
import utn.fc.backend.logistica.repository.ViajeRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Planificador con RoutesClient, PedidosClient y CamionerosClient mockeados (sin Spring ni base).
 * OSRM se simula con 1 hora de conducción por parada, y la descarga es de 20 min por parada,
 * así que el tiempo total con N paradas es N*3600 + N*1200 = 4800*N segundos (lectura "20 min por cada parada").
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanificadorServiceTest {

    private static final long REGION = 1L;
    private static final long ID_CAMIONERO = 7L;

    /** Dependencias simuladas: clientes remotos, OSRM y el repositorio de viajes. */
    @Mock
    CamionerosClient camionerosClient;
    @Mock
    PedidosClient pedidosClient;
    @Mock
    RoutesClient routesClient;
    @Mock
    ViajeRepository viajeRepository;

    /** Servicio bajo prueba (se construye a mano en setUp por las propiedades de configuración). */
    PlanificadorService service;

    /** Crea el servicio y programa OSRM para devolver 1 hora de conducción por parada. */
    @BeforeEach
    void setUp() {
        service = crearServicio(true);
        when(routesClient.calcular(any(Coordenada.class), anyList(), anyBoolean()))
                .thenAnswer(inv -> new RutaCalculada(3600L * ((List<?>) inv.getArgument(1)).size(), 1000L));
    }

    /** Construye el servicio con piso 280 tachos, 20 min de descarga y tope de 10 horas (36.000 s). */
    private PlanificadorService crearServicio(boolean descargaIncluyeUltimaParada) {
        LogisticaProperties props = new LogisticaProperties(
                new LogisticaProperties.Planificacion(280, 20, 36_000, descargaIncluyeUltimaParada, false),
                new LogisticaProperties.Fabrica(-31.44276586787743, -64.19411487348728),
                null, null, null);
        return new PlanificadorService(camionerosClient, pedidosClient, routesClient, viajeRepository, props);
    }

    /** Simula que el siguiente camionero de la cola tiene un camión con la capacidad indicada. */
    private void hayCamionero(int capacidad) {
        when(camionerosClient.siguiente()).thenReturn(Optional.of(new CamioneroRemoto(
                ID_CAMIONERO, "Juan Pérez", "351", EstadoCamionero.DISPONIBLE,
                new CamioneroRemoto.CamionRemoto(3L, "AB123CD", capacidad))));
    }

    /** Crea un pedido PENDIENTE con una heladería de coordenadas distintas según el id. */
    private static PedidoRemoto pedido(long id, int tachos) {
        return new PedidoRemoto(id, tachos, EstadoPedido.PENDIENTE, new PedidoRemoto.HeladeriaRemota(
                id, "Heladería " + id, "Dir " + id,
                BigDecimal.valueOf(-32.0 - id * 0.01), BigDecimal.valueOf(-63.0 - id * 0.01)));
    }

    /** Simula la cola de la región con N pedidos de igual cantidad de tachos. */
    private void hayCola(int cantidad, int tachosCadaUno) {
        List<PedidoRemoto> cola = new ArrayList<>();
        for (long id = 1; id <= cantidad; id++) {
            cola.add(pedido(id, tachosCadaUno));
        }
        when(pedidosClient.obtenerCola(REGION)).thenReturn(cola);
    }

    // ---------------------------------------------------------------- caso feliz

    /** Caso feliz: confirma el viaje, arma la URL y cambia estados en orden (ASIGNADO, EN_TRANSITO, EN_VIAJE). */
    @Test
    void casoFelizConfirmaElViajeYCambiaLosEstados() {
        hayCamionero(400);
        hayCola(5, 60); // 300 tachos, 5*4800 = 24.000 s <= 36.000 s

        ViajeResponse viaje = service.planificar(REGION);

        assertEquals(EstadoViaje.PLANIFICADO, viaje.estado());
        assertEquals(300, viaje.totalTachosCargados());
        assertEquals(24_000, viaje.tiempoEstimadoSeg());
        assertEquals(ID_CAMIONERO, viaje.idCamionero());
        assertEquals(5, viaje.paradas().size());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L), viaje.paradas().stream().map(ViajeResponse.ParadaResponse::idPedido).toList());
        assertEquals(20, viaje.paradas().get(0).tiempoDescargaMin());
        // RN-05: origen vacío => "Tu ubicación"
        assertTrue(viaje.urlGoogleMaps().startsWith("https://www.google.com/maps/dir//-"));
        assertEquals(5, viaje.urlGoogleMaps().substring("https://www.google.com/maps/dir//".length()).split("/").length);

        verify(viajeRepository).save(any(Viaje.class));
        InOrder orden = inOrder(pedidosClient, camionerosClient);
        for (long id = 1; id <= 5; id++) {
            orden.verify(pedidosClient).cambiarEstado(id, EstadoPedido.ASIGNADO);
        }
        for (long id = 1; id <= 5; id++) {
            orden.verify(pedidosClient).cambiarEstado(id, EstadoPedido.EN_TRANSITO);
        }
        orden.verify(camionerosClient).cambiarEstado(ID_CAMIONERO, EstadoCamionero.EN_VIAJE);
    }

    /** Con la lectura alternativa de RN-03 la última parada no suma tiempo de descarga. */
    @Test
    void lecturaAlternativaDeRn03DescuentaLaUltimaDescarga() {
        service = crearServicio(false); // (N-1) * 20 min
        hayCamionero(400);
        hayCola(5, 60);

        ViajeResponse viaje = service.planificar(REGION);

        assertEquals(5 * 3600 + 4 * 1200, viaje.tiempoEstimadoSeg());
    }

    // ---------------------------------------------------------------- RN-02

    /** RN-02: el pedido que excede la capacidad del camión queda afuera y sin tocar. */
    @Test
    void cortePorCapacidadDejaElPedidoExcedentePendiente() {
        hayCamionero(400);
        hayCola(6, 70); // 6 * 70 = 420 > 400 => entran 5 (350)

        ViajeResponse viaje = service.planificar(REGION);

        assertEquals(5, viaje.paradas().size());
        assertEquals(350, viaje.totalTachosCargados());
        verify(pedidosClient, never()).cambiarEstado(eq(6L), any(EstadoPedido.class));
        verify(pedidosClient, times(5)).cambiarEstado(anyLong(), eq(EstadoPedido.ASIGNADO));
    }

    // ---------------------------------------------------------------- RN-03

    /** RN-03: el pedido que haría superar las 10 horas queda afuera y sin tocar. */
    @Test
    void corteDeDiezHorasDejaElPedidoExcedentePendiente() {
        hayCamionero(500);
        hayCola(8, 40); // con 7 paradas: 33.600 s; con 8: 38.400 s > 36.000 s

        ViajeResponse viaje = service.planificar(REGION);

        assertEquals(7, viaje.paradas().size());
        assertEquals(280, viaje.totalTachosCargados());
        assertEquals(7 * 4800, viaje.tiempoEstimadoSeg());
        verify(pedidosClient, never()).cambiarEstado(eq(8L), any(EstadoPedido.class));
    }

    // ---------------------------------------------------------------- piso

    /** Menos de 280 tachos: PlanificacionNoViableException sin guardar ni cambiar estados. */
    @Test
    void pisoDe280NoAlcanzadoAbortaSinTocarNada() {
        hayCamionero(400);
        hayCola(3, 60); // 180 < 280

        assertThrows(PlanificacionNoViableException.class, () -> service.planificar(REGION));

        verify(viajeRepository, never()).save(any());
        verify(pedidosClient, never()).cambiarEstado(anyLong(), any(EstadoPedido.class));
        verify(camionerosClient, never()).cambiarEstado(anyLong(), any(EstadoCamionero.class));
    }

    /** Cola vacía: tampoco alcanza el piso, no se guarda ningún viaje. */
    @Test
    void colaVaciaTampocoAlcanzaElPiso() {
        hayCamionero(400);
        when(pedidosClient.obtenerCola(REGION)).thenReturn(List.of());

        assertThrows(PlanificacionNoViableException.class, () -> service.planificar(REGION));
        verify(viajeRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- RN-04

    /** RN-04: sin camioneros disponibles lanza ConflictoException sin consultar nada más. */
    @Test
    void sinCamionerosDisponiblesDaConflictoYNoTocaNada() {
        when(camionerosClient.siguiente()).thenReturn(Optional.empty());

        assertThrows(ConflictoException.class, () -> service.planificar(REGION));

        verifyNoInteractions(pedidosClient, routesClient, viajeRepository);
        verify(camionerosClient, never()).cambiarEstado(anyLong(), any(EstadoCamionero.class));
    }

    /** Región inexistente en ms-pedidos: se propaga RecursoNoEncontradoException. */
    @Test
    void regionInexistenteSePropagaComoNoEncontrada() {
        hayCamionero(400);
        when(pedidosClient.obtenerCola(99L)).thenThrow(new RecursoNoEncontradoException("No existe la región"));

        assertThrows(RecursoNoEncontradoException.class, () -> service.planificar(99L));
        verify(viajeRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- RN-05: coordenadas inválidas

    /** Crea un pedido PENDIENTE cuya heladería tiene las coordenadas indicadas (pueden ser inválidas o null). */
    private static PedidoRemoto pedidoConCoordenadas(long id, int tachos, BigDecimal lat, BigDecimal lng) {
        return new PedidoRemoto(id, tachos, EstadoPedido.PENDIENTE,
                new PedidoRemoto.HeladeriaRemota(id, "Heladería " + id, "Dir " + id, lat, lng));
    }

    /** Pedido con heladería en (0,0): coordenadas que se consideran "sin cargar". */
    private static PedidoRemoto pedidoSinUbicacion(long id, int tachos) {
        return pedidoConCoordenadas(id, tachos, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** (a) Un pedido inválido en el medio de la cola se salta: el viaje se arma con los demás y se pide su reencolado. */
    @Test
    void pedidoConCoordenadasInvalidasEnElMedioSeSaltaYSeReencola() {
        hayCamionero(400);
        when(pedidosClient.obtenerCola(REGION)).thenReturn(List.of(
                pedido(1, 60), pedido(2, 60), pedidoSinUbicacion(3, 60), pedido(4, 60), pedido(5, 60), pedido(6, 60)));

        ViajeResponse viaje = service.planificar(REGION);

        assertEquals(List.of(1L, 2L, 4L, 5L, 6L),
                viaje.paradas().stream().map(ViajeResponse.ParadaResponse::idPedido).toList());
        assertEquals(300, viaje.totalTachosCargados());
        // la URL tiene solo las 5 paradas válidas y nunca la coordenada 0,0
        assertEquals(5, viaje.urlGoogleMaps().substring("https://www.google.com/maps/dir//".length()).split("/").length);
        assertFalse(viaje.urlGoogleMaps().contains("0.000000,0.000000"));
        // el rechazado no se toca en ms-pedidos (sigue PENDIENTE): solo se lo manda al final de la cola,
        // y recién después de confirmar el viaje (camionero EN_VIAJE)
        verify(pedidosClient, never()).cambiarEstado(eq(3L), any(EstadoPedido.class));
        InOrder orden = inOrder(camionerosClient, pedidosClient);
        orden.verify(camionerosClient).cambiarEstado(ID_CAMIONERO, EstadoCamionero.EN_VIAJE);
        orden.verify(pedidosClient).reencolarAlFinal(3L);
        verify(pedidosClient, times(1)).reencolarAlFinal(anyLong());
    }

    /** (b) El rechazado no suma tachos ni tiempo, no genera llamada a Routes y no provoca el corte por capacidad. */
    @Test
    void pedidoRechazadoNoSumaTachosNiTiempo() {
        hayCamionero(350); // exactamente 5 * 70: si el rechazado (80) sumara, el 5º pedido válido quedaría afuera
        when(pedidosClient.obtenerCola(REGION)).thenReturn(List.of(
                pedido(1, 70),
                pedidoConCoordenadas(9, 80, BigDecimal.valueOf(95), BigDecimal.valueOf(-64)), // latitud fuera de rango
                pedido(2, 70), pedido(3, 70), pedido(4, 70), pedido(5, 70)));

        ViajeResponse viaje = service.planificar(REGION);

        assertEquals(5, viaje.paradas().size());
        assertEquals(350, viaje.totalTachosCargados());
        assertEquals(5 * 4800, viaje.tiempoEstimadoSeg()); // 5 paradas, no 6
        // una llamada a Routes por candidato válido, y ninguna incluye la coordenada inválida
        verify(routesClient, times(5)).calcular(any(Coordenada.class), anyList(), anyBoolean());
        verify(routesClient, never()).calcular(any(Coordenada.class),
                argThat(l -> l.contains(new Coordenada(95, -64))), anyBoolean());
        verify(pedidosClient).reencolarAlFinal(9L);
    }

    /** (c) Si falla el reencolado del rechazado, la planificación igual se confirma (solo se registra un warn). */
    @Test
    void fallaDelReencoladoNoRompeLaPlanificacion() {
        hayCamionero(400);
        when(pedidosClient.obtenerCola(REGION)).thenReturn(List.of(
                pedidoSinUbicacion(1, 60), pedido(2, 60), pedido(3, 60), pedido(4, 60), pedido(5, 60), pedido(6, 60)));
        doThrow(new ServicioExternoException("ms-pedidos caído", HttpStatus.SERVICE_UNAVAILABLE))
                .when(pedidosClient).reencolarAlFinal(1L);

        ViajeResponse viaje = service.planificar(REGION);

        assertEquals(EstadoViaje.PLANIFICADO, viaje.estado());
        assertEquals(5, viaje.paradas().size());
        verify(pedidosClient).reencolarAlFinal(1L);
        verify(camionerosClient).cambiarEstado(ID_CAMIONERO, EstadoCamionero.EN_VIAJE);
        verify(viajeRepository, never()).delete(any(Viaje.class));
        verify(pedidosClient, never()).cambiarEstado(anyLong(), eq(EstadoPedido.PENDIENTE));
    }

    /** (d) Todos los pedidos inválidos: 422 de piso, se intenta reencolarlos y no se toca el camionero ni se guarda nada. */
    @Test
    void todosLosPedidosInvalidosDan422YSoloSeReencolan() {
        hayCamionero(400);
        when(pedidosClient.obtenerCola(REGION)).thenReturn(List.of(
                pedidoSinUbicacion(1, 80),
                pedidoConCoordenadas(2, 80, null, BigDecimal.valueOf(-64)),
                new PedidoRemoto(3L, 80, EstadoPedido.PENDIENTE, null),
                pedidoConCoordenadas(4, 80, BigDecimal.valueOf(-31), BigDecimal.valueOf(200))));

        PlanificacionNoViableException error =
                assertThrows(PlanificacionNoViableException.class, () -> service.planificar(REGION));

        assertTrue(error.getMessage().contains("RN-05"));
        assertTrue(error.getMessage().contains("[1, 2, 3, 4]"));
        verifyNoInteractions(routesClient);
        verify(viajeRepository, never()).save(any());
        verify(pedidosClient, never()).cambiarEstado(anyLong(), any(EstadoPedido.class));
        verify(camionerosClient, never()).cambiarEstado(anyLong(), any(EstadoCamionero.class));
        for (long id = 1; id <= 4; id++) {
            verify(pedidosClient).reencolarAlFinal(id);
        }
    }

    /** (d) Piso no alcanzado con los válidos: 422 sin tocar camionero ni pedidos válidos; el rechazado sí se reencola. */
    @Test
    void pisoNoAlcanzadoConRechazadosNoTocaLosValidos() {
        hayCamionero(400);
        when(pedidosClient.obtenerCola(REGION)).thenReturn(List.of(
                pedido(1, 60), pedidoSinUbicacion(2, 80), pedido(3, 60), pedido(4, 60), pedido(5, 60))); // 240 < 280

        PlanificacionNoViableException error =
                assertThrows(PlanificacionNoViableException.class, () -> service.planificar(REGION));

        assertTrue(error.getMessage().contains("acumulado: 240"));
        assertTrue(error.getMessage().contains("RN-05"));
        verify(viajeRepository, never()).save(any());
        verify(pedidosClient, never()).cambiarEstado(anyLong(), any(EstadoPedido.class));
        verify(camionerosClient, never()).cambiarEstado(anyLong(), any(EstadoCamionero.class));
        verify(pedidosClient).reencolarAlFinal(2L);
        verify(pedidosClient, times(1)).reencolarAlFinal(anyLong());
    }

    /** Si la confirmación falla y se compensa, los rechazados NO se reencolan (la planificación se revirtió completa). */
    @Test
    void fallaRemotaAlConfirmarNoReencolaLosRechazados() {
        hayCamionero(400);
        when(pedidosClient.obtenerCola(REGION)).thenReturn(List.of(
                pedidoSinUbicacion(1, 60), pedido(2, 60), pedido(3, 60), pedido(4, 60), pedido(5, 60), pedido(6, 60)));
        doThrow(new ServicioExternoException("ms-camioneros caído", HttpStatus.BAD_GATEWAY))
                .when(camionerosClient).cambiarEstado(ID_CAMIONERO, EstadoCamionero.EN_VIAJE);

        assertThrows(ServicioExternoException.class, () -> service.planificar(REGION));

        verify(pedidosClient, never()).reencolarAlFinal(anyLong());
        verify(viajeRepository).delete(any(Viaje.class));
    }

    /** Validación de coordenadas (RN-05): null, fuera de rango y 0,0 son inválidas; el resto es válido. */
    @Test
    void validacionDeCoordenadas() {
        assertNull(PlanificadorService.coordenadaValida(new PedidoRemoto(1L, 60, EstadoPedido.PENDIENTE, null)));
        assertNull(PlanificadorService.coordenadaValida(pedidoConCoordenadas(1, 60, null, BigDecimal.ONE)));
        assertNull(PlanificadorService.coordenadaValida(pedidoConCoordenadas(1, 60, BigDecimal.ONE, null)));
        assertNull(PlanificadorService.coordenadaValida(pedidoConCoordenadas(1, 60, BigDecimal.valueOf(-91), BigDecimal.ONE)));
        assertNull(PlanificadorService.coordenadaValida(pedidoConCoordenadas(1, 60, BigDecimal.ONE, BigDecimal.valueOf(181))));
        assertNull(PlanificadorService.coordenadaValida(pedidoSinUbicacion(1, 60)));
        assertNull(PlanificadorService.coordenadaValida(
                pedidoConCoordenadas(1, 60, new BigDecimal("0.000000"), new BigDecimal("0.0"))));
        // una sola en 0 no es "0,0": es válida (ej.: ecuador o meridiano de Greenwich)
        assertEquals(new Coordenada(0, -64.0),
                PlanificadorService.coordenadaValida(pedidoConCoordenadas(1, 60, BigDecimal.ZERO, BigDecimal.valueOf(-64))));
        assertEquals(new Coordenada(-31.44, -64.19),
                PlanificadorService.coordenadaValida(pedidoConCoordenadas(1, 60, new BigDecimal("-31.44"), new BigDecimal("-64.19"))));
    }

    // ---------------------------------------------------------------- OSRM
    /** Error de OSRM: se propaga sin guardar el viaje ni cambiar estados. */
    @Test
    void errorDeGoogleSePropagaSinDejarEstadoAMedias() {
        hayCamionero(400);
        hayCola(5, 60);
        when(routesClient.calcular(any(Coordenada.class), anyList(), anyBoolean()))
                .thenThrow(new ServicioExternoException("OSRM caído", HttpStatus.BAD_GATEWAY));

        assertThrows(ServicioExternoException.class, () -> service.planificar(REGION));

        verify(viajeRepository, never()).save(any());
        verify(pedidosClient, never()).cambiarEstado(anyLong(), any(EstadoPedido.class));
        verify(camionerosClient, never()).cambiarEstado(anyLong(), any(EstadoCamionero.class));
    }

    // ---------------------------------------------------------------- falla remota + compensación

    /** Falla al asignar un pedido: compensa los ya asignados a PENDIENTE y borra el viaje. */
    @Test
    void fallaAlAsignarUnPedidoRevierteLosYaModificadosYDescartaElViaje() {
        hayCamionero(400);
        hayCola(5, 60);
        doThrow(new ServicioExternoException("ms-pedidos caído", HttpStatus.SERVICE_UNAVAILABLE))
                .when(pedidosClient).cambiarEstado(3L, EstadoPedido.ASIGNADO);

        ServicioExternoException error = assertThrows(ServicioExternoException.class, () -> service.planificar(REGION));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        // 1 y 2 estaban ASIGNADO y 3 quedó en duda: se intenta ASIGNADO -> PENDIENTE en los tres
        verify(pedidosClient).cambiarEstado(1L, EstadoPedido.PENDIENTE);
        verify(pedidosClient).cambiarEstado(2L, EstadoPedido.PENDIENTE);
        verify(pedidosClient).cambiarEstado(3L, EstadoPedido.PENDIENTE);
        verify(pedidosClient, never()).cambiarEstado(eq(4L), any(EstadoPedido.class));
        verify(pedidosClient, never()).cambiarEstado(anyLong(), eq(EstadoPedido.EN_TRANSITO));
        verify(camionerosClient, never()).cambiarEstado(anyLong(), any(EstadoCamionero.class));
        verify(viajeRepository).delete(any(Viaje.class));
    }

    /** Falla al poner al camionero EN_VIAJE: revierte los pedidos, lo vuelve a DISPONIBLE y borra el viaje. */
    @Test
    void fallaAlPonerAlCamioneroEnViajeRevierteTodoYLoDevuelveADisponible() {
        hayCamionero(400);
        hayCola(5, 60);
        doThrow(new ServicioExternoException("ms-camioneros caído", HttpStatus.BAD_GATEWAY))
                .when(camionerosClient).cambiarEstado(ID_CAMIONERO, EstadoCamionero.EN_VIAJE);

        assertThrows(ServicioExternoException.class, () -> service.planificar(REGION));

        // los 5 ya estaban EN_TRANSITO: la vuelta a PENDIENTE es EN_TRANSITO -> NO_ENTREGADO (ms-pedidos lo reencola)
        for (long id = 1; id <= 5; id++) {
            verify(pedidosClient).cambiarEstado(id, EstadoPedido.NO_ENTREGADO);
        }
        verify(camionerosClient).cambiarEstado(ID_CAMIONERO, EstadoCamionero.DISPONIBLE);
        verify(viajeRepository).delete(any(Viaje.class));
    }

    // ---------------------------------------------------------------- enlace

    /** La URL de Google Maps deja el origen vacío y lista las paradas en orden. */
    @Test
    void urlConOrigenVacioYParadasEnOrden() {
        String url = PlanificadorService.construirUrl(
                List.of(new Coordenada(-31.5, -64.1), new Coordenada(-32.0, -63.0)),
                new Coordenada(-31.44276586787743, -64.19411487348728), false);

        assertEquals("https://www.google.com/maps/dir//-31.500000,-64.100000/-32.000000,-63.000000", url);
    }

    /** Con vuelta a fábrica, la URL agrega la fábrica como último destino. */
    @Test
    void urlConVueltaAFabricaAgregaElOrigenAlFinal() {
        String url = PlanificadorService.construirUrl(
                List.of(new Coordenada(-31.5, -64.1)),
                new Coordenada(-31.44276586787743, -64.19411487348728), true);

        assertEquals("https://www.google.com/maps/dir//-31.500000,-64.100000/-31.442766,-64.194115", url);
    }
}
