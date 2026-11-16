package utn.fc.backend.logistica.client;

import org.junit.jupiter.api.Test;
import utn.fc.backend.logistica.exception.ServicioExternoException;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitarios de OsrmRoutesClient sin Spring ni red: armado de la URL (orden longitud,latitud) y
 * validación/conversión de la respuesta de OSRM.
 */
class OsrmRoutesClientTest {

    private static final Coordenada ORIGEN = new Coordenada(-31.44, -64.19);
    private static final Coordenada PARADA_1 = new Coordenada(-32.0, -63.0);
    private static final Coordenada PARADA_2 = new Coordenada(-33.5, -62.5);
    private static final String SUFIJO = "?overview=false&alternatives=false&steps=false";

    /** OSRM usa longitud,latitud y separa los puntos con punto y coma; el origen va primero. */
    @Test
    void armaLaUrlConLongitudPrimeroYOrigenAlPrincipio() {
        URI uri = OsrmRoutesClient.construirUri("http://osrm:5000", ORIGEN, List.of(PARADA_1, PARADA_2), false);

        assertEquals("http://osrm:5000/route/v1/driving/"
                + "-64.190000,-31.440000;-63.000000,-32.000000;-62.500000,-33.500000" + SUFIJO, uri.toString());
    }

    /** Con volverAlOrigen la ruta termina en la fábrica. */
    @Test
    void agregaElOrigenAlFinalSiVuelveAFabrica() {
        URI uri = OsrmRoutesClient.construirUri("http://osrm:5000", ORIGEN, List.of(PARADA_1), true);

        assertEquals("http://osrm:5000/route/v1/driving/"
                + "-64.190000,-31.440000;-63.000000,-32.000000;-64.190000,-31.440000" + SUFIJO, uri.toString());
    }

    /** Una barra final en la URL base no genera "//" en el path. */
    @Test
    void ignoraLaBarraFinalDeLaUrlBase() {
        URI uri = OsrmRoutesClient.construirUri("https://router.project-osrm.org/", ORIGEN, List.of(PARADA_1), false);

        assertEquals("https://router.project-osrm.org/route/v1/driving/"
                + "-64.190000,-31.440000;-63.000000,-32.000000" + SUFIJO, uri.toString());
    }

    /** Respuesta Ok: redondea duración (s) y distancia (m). */
    @Test
    void convierteLaRespuestaOk() {
        OsrmRoutesClient.OsrmResponse respuesta = new OsrmRoutesClient.OsrmResponse("Ok",
                List.of(new OsrmRoutesClient.OsrmResponse.Route(1234.6, 5678.4)));

        RutaCalculada ruta = OsrmRoutesClient.aRuta(respuesta);

        assertEquals(1235L, ruta.duracionSegundos());
        assertEquals(5678L, ruta.distanciaMetros());
    }

    /** Sin ruta posible (code distinto de Ok): 502. */
    @Test
    void codeDistintoDeOkDa502() {
        OsrmRoutesClient.OsrmResponse respuesta = new OsrmRoutesClient.OsrmResponse("NoRoute", List.of());

        ServicioExternoException e = assertThrows(ServicioExternoException.class,
                () -> OsrmRoutesClient.aRuta(respuesta));
        assertEquals(502, e.getStatus().value());
    }

    /** Respuesta vacía, sin rutas o sin duración: 502. */
    @Test
    void respuestaIncompletaDa502() {
        assertThrows(ServicioExternoException.class, () -> OsrmRoutesClient.aRuta(null));
        assertThrows(ServicioExternoException.class,
                () -> OsrmRoutesClient.aRuta(new OsrmRoutesClient.OsrmResponse("Ok", null)));
        assertThrows(ServicioExternoException.class,
                () -> OsrmRoutesClient.aRuta(new OsrmRoutesClient.OsrmResponse("Ok", List.of())));
        assertThrows(ServicioExternoException.class,
                () -> OsrmRoutesClient.aRuta(new OsrmRoutesClient.OsrmResponse("Ok",
                        List.of(new OsrmRoutesClient.OsrmResponse.Route(null, 100.0)))));
    }
}
