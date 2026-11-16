package utn.fc.backend.logistica.client;

import java.util.List;

/** Abstracción del servicio de rutas (OSRM) para poder mockearla en tests. */
public interface RoutesClient {

    /**
     * Duración de conducir origen -> paradas (en orden). Si {@code volverAlOrigen}, la ruta termina en el origen.
     * Lanza ServicioExternoException si OSRM falla o no devuelve una ruta válida.
     */
    RutaCalculada calcular(Coordenada origen, List<Coordenada> paradas, boolean volverAlOrigen);
}
