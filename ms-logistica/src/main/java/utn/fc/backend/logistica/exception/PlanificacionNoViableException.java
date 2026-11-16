package utn.fc.backend.logistica.exception;

/** La planificación no puede confirmarse (piso de tachos, coordenadas inválidas...). HTTP 422. */
public class PlanificacionNoViableException extends RuntimeException {

    public PlanificacionNoViableException(String message) {
        super(message);
    }
}
