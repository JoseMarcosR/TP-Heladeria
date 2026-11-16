package utn.fc.backend.logistica.client;

/** Resultado de OSRM: duración de conducción (sin descargas) y distancia. */
public record RutaCalculada(long duracionSegundos, long distanciaMetros) {
}
