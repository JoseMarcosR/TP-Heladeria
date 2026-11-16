package utn.fc.backend.camioneros.exception;

/** Operación incompatible con el estado actual o datos duplicados (HTTP 409). */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String message) {
        super(message);
    }
}
