package utn.fc.backend.logistica.exception;

/** Solicitud que viola una regla de negocio (HTTP 400). */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String message) {
        super(message);
    }
}
