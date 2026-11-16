package utn.fc.backend.logistica.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Parámetros de negocio e integración (ver application.yml, prefijo "logistica"). */
@ConfigurationProperties(prefix = "logistica")
public record LogisticaProperties(
        Planificacion planificacion,
        Fabrica fabrica,
        Routes routes,
        Servicios servicios,
        Keycloak keycloak
) {

    /**
     * Reglas de la planificación.
     *
     * @param pisoMinimoTachos            tachos acumulados mínimos para despachar un viaje (default 280); si no se
     *                                    alcanza la planificación responde 422 sin modificar nada
     * @param tiempoDescargaMin           minutos de descarga por parada (RN-03, default 20)
     * @param limiteViajeSegundos         tope de cadena de frío (RN-03, default 36.000 s = 10 h)
     * @param descargaIncluyeUltimaParada lectura de RN-03. Tiempo Total = conducción + descargas, donde
     *                                    descargas = N * tiempoDescargaMin si es true (decisión del equipo, default) o
     *                                    (N-1) * tiempoDescargaMin si es false (fórmula literal del contrato)
     * @param incluirVueltaAFabrica       si la ruta, el tiempo y el enlace cuentan el regreso a la fábrica
     *                                    (default false: el viaje termina en la última heladería)
     */
    public record Planificacion(
            int pisoMinimoTachos,
            int tiempoDescargaMin,
            long limiteViajeSegundos,
            boolean descargaIncluyeUltimaParada,
            boolean incluirVueltaAFabrica
    ) {
    }

    /** Origen de todas las rutas: la fábrica, ubicada en la UTN FRC (default -31.44276586787743, -64.19411487348728). */
    public record Fabrica(double latitud, double longitud) {
    }

    /** OSRM (servicio de rutas). Sin API key: la URL base se configura con OSRM_URL. */
    public record Routes(String url, int connectTimeoutMs, int readTimeoutMs) {
    }

    /** URLs directas (sin gateway) y timeouts para ms-pedidos y ms-camioneros. */
    public record Servicios(String pedidosUrl, String camionerosUrl, int connectTimeoutMs, int readTimeoutMs) {
    }

    /** Client credentials del cliente confidencial "ms-logistica" (rol ServicioInterno). */
    public record Keycloak(String tokenUri, String clientId, String clientSecret) {
    }
}
