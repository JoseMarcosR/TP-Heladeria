package utn.fc.backend.pedidos.exception;

/** Operación incompatible con el estado actual del recurso (HTTP 409). */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String message) {
        super(message);
    }
}
