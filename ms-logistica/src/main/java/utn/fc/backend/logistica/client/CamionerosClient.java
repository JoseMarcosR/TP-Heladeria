package utn.fc.backend.logistica.client;

import java.util.Optional;

/** Acceso a ms-camioneros (solo endpoints permitidos a ServicioInterno o Administrador). */
public interface CamionerosClient {

    /** Próximo camionero de la cola FIFO global (RN-04); vacío si no hay ninguno DISPONIBLE. */
    Optional<CamioneroRemoto> siguiente();

    void cambiarEstado(Long idCamionero, EstadoCamionero estado);
}
