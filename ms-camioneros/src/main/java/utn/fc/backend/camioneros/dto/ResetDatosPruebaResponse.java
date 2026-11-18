package utn.fc.backend.camioneros.dto;

/**
 * Resultado del reset de datos de prueba: cuántos camioneros (con su camión) se borraron y cuántos se volvieron
 * a sembrar.
 */
public record ResetDatosPruebaResponse(long camionerosEliminados, long camionerosSembrados) {
}
