package utn.fc.backend.camioneros.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.camioneros.dto.CamionResponse;
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
import java.util.Locale;

@Service
public class CamioneroService {

    private final CamioneroRepository camioneroRepository;
    private final CamionRepository camionRepository;

    public CamioneroService(CamioneroRepository camioneroRepository, CamionRepository camionRepository) {
        this.camioneroRepository = camioneroRepository;
        this.camionRepository = camionRepository;
    }

    /** FUN-02: inscribe al camionero con su camión; queda DISPONIBLE al final de la cola global. */
    @Transactional
    public CamioneroResponse registrar(CamioneroCreateRequest request) {
        String dni = request.dni().trim();
        String patente = request.camion().patente().trim().toUpperCase(Locale.ROOT);

        if (camioneroRepository.existsByDni(dni)) {
            throw new ConflictoException("Ya existe un camionero con DNI " + dni);
        }
        if (camionRepository.existsByPatente(patente)) {
            throw new ConflictoException("Ya existe un camión con patente " + patente);
        }

        Camion camion = new Camion(patente, request.camion().capacidadMaxTachos());
        boolean contratoActivo = request.contratoActivo() == null || request.contratoActivo();
        Camionero camionero = new Camionero(camion, dni, request.nombreCompleto().trim(),
                request.telefono().trim(), contratoActivo);
        return CamioneroResponse.from(camioneroRepository.save(camionero));
    }

    /**
     * RN-04: primer camionero de la cola global FIFO con estado DISPONIBLE y contrato activo.
     * Es una consulta: no lo reserva. El cambio a EN_VIAJE lo hace quien planifica vía PUT /estado.
     */
    @Transactional(readOnly = true)
    public CamioneroResponse siguiente() {
        List<Camionero> cola = camioneroRepository.findCola(EstadoCamionero.DISPONIBLE, PageRequest.of(0, 1));
        if (cola.isEmpty()) {
            throw new RecursoNoEncontradoException("No hay camioneros DISPONIBLES con contrato activo (RN-04)");
        }
        return CamioneroResponse.from(cola.get(0));
    }

    /**
     * DISPONIBLE -> EN_VIAJE (al asignarse a una ruta) y EN_VIAJE -> DISPONIBLE (cierre del viaje).
     * Al volver a DISPONIBLE se actualiza ultima_actualizacion_cola: pasa al final de la fila.
     */
    @Transactional
    public CamioneroResponse cambiarEstado(Long id, EstadoCamionero destino) {
        Camionero camionero = camioneroRepository.findDetalleById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el camionero con id " + id));

        if (camionero.getEstado() == destino) {
            throw new ConflictoException("El camionero ya se encuentra en estado " + destino);
        }
        if (destino == EstadoCamionero.EN_VIAJE && !camionero.isContratoActivo()) {
            throw new ConflictoException("El camionero no tiene contrato activo");
        }

        camionero.setEstado(destino);
        if (destino == EstadoCamionero.DISPONIBLE) {
            camionero.setUltimaActualizacionCola(Instant.now());
        }
        return CamioneroResponse.from(camionero);
    }

    /** RN-02: especificaciones y capacidad de freezer de un camión. */
    @Transactional(readOnly = true)
    public CamionResponse obtenerCamion(Long id) {
        return camionRepository.findById(id)
                .map(CamionResponse::from)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el camión con id " + id));
    }
}
