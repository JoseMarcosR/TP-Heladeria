package utn.fc.backend.logistica.model;

public enum EstadoViaje {
    /** Viaje armado que cumple capacidad del camión y límite de 10 horas. */
    PLANIFICADO,
    /** Cerrado manualmente por el Administrador tras el reporte del camionero. */
    FINALIZADO
}
