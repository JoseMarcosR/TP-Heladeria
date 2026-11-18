package utn.fc.backend.pedidos.dto;

/**
 * Resultado del reset de datos de prueba: cuántos pedidos se borraron y cuántos se volvieron a sembrar.
 * Las regiones, ciudades y heladerías (catálogo geográfico) nunca se borran.
 */
public record ResetDatosPruebaResponse(long pedidosEliminados, long pedidosSembrados) {
}
