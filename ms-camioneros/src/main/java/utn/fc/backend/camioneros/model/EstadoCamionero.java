package utn.fc.backend.camioneros.model;

public enum EstadoCamionero {
    /** En la cola global, listo para ser asignado a un viaje. */
    DISPONIBLE,
    /** Asignado a una hoja de ruta activa. */
    EN_VIAJE
}
