package utn.fc.backend.logistica.exception;

import org.springframework.http.HttpStatus;

/** Falla de un sistema externo (OSRM, ms-pedidos, ms-camioneros, Keycloak): 502 o 503. */
public class ServicioExternoException extends RuntimeException {

    private final HttpStatus status;

    public ServicioExternoException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public ServicioExternoException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
