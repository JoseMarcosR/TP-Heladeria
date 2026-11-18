package utn.fc.backend.logistica.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.logistica.repository.ViajeRepository;

/**
 * Reset de datos de prueba (solo para demo/testing): borra TODOS los viajes y sus paradas.
 * Se usa junto con los resets de ms-pedidos y ms-camioneros para que la colección de Postman corra siempre
 * sobre una base limpia (sin hojas de ruta que apunten a pedidos/camioneros que ya no existen).
 */
@Service
public class DatosPruebaService {

    private static final Logger log = LoggerFactory.getLogger(DatosPruebaService.class);

    private final ViajeRepository viajeRepository;

    public DatosPruebaService(ViajeRepository viajeRepository) {
        this.viajeRepository = viajeRepository;
    }

    @Transactional
    public long resetear() {
        long eliminados = viajeRepository.count();
        // deleteAll (no deleteAllInBatch) para que JPA borre en cascada las paradas de cada viaje.
        viajeRepository.deleteAll();
        log.warn("Reset de datos de prueba en ms-logistica: {} viajes eliminados", eliminados);
        return eliminados;
    }
}
