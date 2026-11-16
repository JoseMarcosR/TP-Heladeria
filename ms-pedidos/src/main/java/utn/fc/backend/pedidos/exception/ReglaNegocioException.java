package utn.fc.backend.pedidos.exception;

/** Violación de una regla de negocio (HTTP 400). */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String message) {
        super(message);
    }
}
