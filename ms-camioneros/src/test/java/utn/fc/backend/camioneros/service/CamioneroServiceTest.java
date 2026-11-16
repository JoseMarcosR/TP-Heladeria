package utn.fc.backend.camioneros.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import utn.fc.backend.camioneros.dto.CamionRequest;
import utn.fc.backend.camioneros.dto.CamioneroCreateRequest;
import utn.fc.backend.camioneros.dto.CamioneroResponse;
import utn.fc.backend.camioneros.exception.ConflictoException;
import utn.fc.backend.camioneros.exception.RecursoNoEncontradoException;
import utn.fc.backend.camioneros.model.Camion;
import utn.fc.backend.camioneros.model.Camionero;
import utn.fc.backend.camioneros.model.EstadoCamionero;
import utn.fc.backend.camioneros.repository.CamionRepository;
import utn.fc.backend.camioneros.repository.CamioneroRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de CamioneroService con Mockito (repositorios mockeados, sin Spring ni base de datos).
 * Cubren el registro de camioneros, la cola FIFO global (RN-04) y los cambios de estado.
 */
@ExtendWith(MockitoExtension.class)
class CamioneroServiceTest {

    /** Repositorios simulados: se programan con when(...) en cada test. */
    @Mock
    CamioneroRepository camioneroRepository;
    @Mock
    CamionRepository camionRepository;

    /** Servicio bajo prueba, con los mocks inyectados. */
    @InjectMocks
    CamioneroService service;

    /** Crea un camionero de prueba (DISPONIBLE) con un camión de 400 tachos. */
    private static Camionero camionero(String dni, boolean contratoActivo) {
        return new Camionero(new Camion("AB123CD", 400), dni, "Juan Pérez", "3511234567", contratoActivo);
    }

    // ---------- registro (FUN-02) ----------

    /** Alta válida: el camionero queda DISPONIBLE, con contrato activo y la patente en mayúsculas. */
    @Test
    void registrarCamioneroQuedaDisponibleYNormalizaLaPatente() {
        when(camioneroRepository.existsByDni("30111222")).thenReturn(false);
        when(camionRepository.existsByPatente("AB123CD")).thenReturn(false);
        when(camioneroRepository.save(any(Camionero.class))).thenAnswer(inv -> inv.getArgument(0));

        CamioneroResponse r = service.registrar(new CamioneroCreateRequest(
                "30111222", "Juan Pérez", "3511234567", null, new CamionRequest("ab123cd", 350)));

        assertEquals(EstadoCamionero.DISPONIBLE, r.estado());
        assertTrue(r.contratoActivo());
        assertEquals("AB123CD", r.camion().patente());
        assertEquals(350, r.camion().capacidadMaxTachos());
    }

    /** DNI ya registrado: lanza ConflictoException y no guarda nada. */
    @Test
    void registrarConDniRepetidoDaConflicto() {
        when(camioneroRepository.existsByDni("30111222")).thenReturn(true);

        assertThrows(ConflictoException.class, () -> service.registrar(new CamioneroCreateRequest(
                "30111222", "Juan", "351", true, new CamionRequest("AB123CD", 350))));
        verify(camioneroRepository, never()).save(any());
    }

    /** Patente ya registrada (sin importar mayúsculas): lanza ConflictoException. */
    @Test
    void registrarConPatenteRepetidaDaConflicto() {
        when(camioneroRepository.existsByDni("30111222")).thenReturn(false);
        when(camionRepository.existsByPatente("AB123CD")).thenReturn(true);

        assertThrows(ConflictoException.class, () -> service.registrar(new CamioneroCreateRequest(
                "30111222", "Juan", "351", true, new CamionRequest("ab123cd", 350))));
    }

    // ---------- cola FIFO global (RN-04) ----------

    /** Devuelve el primer camionero DISPONIBLE de la cola, pidiendo solo una página de tamaño 1. */
    @Test
    void siguienteDevuelveElPrimeroDeLaCola() {
        Camionero primero = camionero("1", true);
        when(camioneroRepository.findCola(eq(EstadoCamionero.DISPONIBLE), any(Pageable.class)))
                .thenReturn(List.of(primero));

        CamioneroResponse r = service.siguiente();

        assertEquals("1", r.dni());
        verify(camioneroRepository).findCola(EstadoCamionero.DISPONIBLE, PageRequest.of(0, 1));
    }

    /** Sin camioneros disponibles: lanza RecursoNoEncontradoException (404). */
    @Test
    void siguienteSinCandidatosDa404() {
        when(camioneroRepository.findCola(eq(EstadoCamionero.DISPONIBLE), any(Pageable.class)))
                .thenReturn(List.of());

        assertThrows(RecursoNoEncontradoException.class, () -> service.siguiente());
    }

    // ---------- cambios de estado ----------

    /** Pasar a EN_VIAJE cambia el estado pero no la marca de tiempo de la cola. */
    @Test
    void pasarAEnViajeNoAlteraLaPosicionEnLaCola() {
        Camionero c = camionero("1", true);
        c.setUltimaActualizacionCola(Instant.parse("2026-01-01T00:00:00Z"));
        when(camioneroRepository.findDetalleById(1L)).thenReturn(Optional.of(c));

        service.cambiarEstado(1L, EstadoCamionero.EN_VIAJE);

        assertEquals(EstadoCamionero.EN_VIAJE, c.getEstado());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), c.getUltimaActualizacionCola());
    }

    /** Volver a DISPONIBLE actualiza la marca de tiempo: queda al final de la cola. */
    @Test
    void volverADisponibleLoMandaAlFinalDeLaCola() {
        Camionero c = camionero("1", true);
        c.setEstado(EstadoCamionero.EN_VIAJE);
        Instant antes = Instant.parse("2026-01-01T00:00:00Z");
        c.setUltimaActualizacionCola(antes);
        when(camioneroRepository.findDetalleById(1L)).thenReturn(Optional.of(c));

        service.cambiarEstado(1L, EstadoCamionero.DISPONIBLE);

        assertEquals(EstadoCamionero.DISPONIBLE, c.getEstado());
        assertTrue(c.getUltimaActualizacionCola().isAfter(antes));
    }

    /** Pedir el mismo estado que ya tiene: lanza ConflictoException. */
    @Test
    void repetirElMismoEstadoDaConflicto() {
        Camionero c = camionero("1", true); // ya DISPONIBLE
        when(camioneroRepository.findDetalleById(1L)).thenReturn(Optional.of(c));

        assertThrows(ConflictoException.class, () -> service.cambiarEstado(1L, EstadoCamionero.DISPONIBLE));
    }

    /** Sin contrato activo no puede pasar a EN_VIAJE: conflicto y el estado no cambia. */
    @Test
    void sinContratoActivoNoPuedeIrEnViaje() {
        Camionero c = camionero("1", false);
        when(camioneroRepository.findDetalleById(1L)).thenReturn(Optional.of(c));

        assertThrows(ConflictoException.class, () -> service.cambiarEstado(1L, EstadoCamionero.EN_VIAJE));
        assertEquals(EstadoCamionero.DISPONIBLE, c.getEstado());
    }

    /** Cambiar estado de un camionero inexistente: lanza RecursoNoEncontradoException. */
    @Test
    void cambiarEstadoDeCamioneroInexistenteDa404() {
        when(camioneroRepository.findDetalleById(9L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.cambiarEstado(9L, EstadoCamionero.EN_VIAJE));
    }

    /** Consultar un camión inexistente: lanza RecursoNoEncontradoException. */
    @Test
    void camionInexistenteDa404() {
        when(camionRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.obtenerCamion(9L));
    }
}
