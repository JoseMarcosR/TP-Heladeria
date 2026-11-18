package utn.fc.backend.camioneros.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.camioneros.config.DataSeeder;
import utn.fc.backend.camioneros.dto.ResetDatosPruebaResponse;
import utn.fc.backend.camioneros.repository.CamionRepository;
import utn.fc.backend.camioneros.repository.CamioneroRepository;

/**
 * Reset de datos de prueba (solo para demo/testing): vacía la cola FIFO global borrando TODOS los camioneros y
 * sus camiones.
 * <p>
 * Permite que la colección de Postman corra siempre sobre una flota vacía, sin depender de lo que cargue el
 * {@link DataSeeder}. Con {@code resembrar = true} vuelve a cargar los 16 camioneros de demo del DataSeeder.
 */
@Service
public class DatosPruebaService {

    private static final Logger log = LoggerFactory.getLogger(DatosPruebaService.class);

    private final CamioneroRepository camioneroRepository;
    private final CamionRepository camionRepository;
    private final DataSeeder dataSeeder;

    public DatosPruebaService(CamioneroRepository camioneroRepository, CamionRepository camionRepository,
                              DataSeeder dataSeeder) {
        this.camioneroRepository = camioneroRepository;
        this.camionRepository = camionRepository;
        this.dataSeeder = dataSeeder;
    }

    @Transactional
    public ResetDatosPruebaResponse resetear(boolean resembrar) {
        long eliminados = camioneroRepository.count();
        // Primero los camioneros (tienen la FK id_camion) y después los camiones.
        camioneroRepository.deleteAllInBatch();
        camionRepository.deleteAllInBatch();

        long sembrados = 0;
        if (resembrar) {
            dataSeeder.sembrarFlotaDemo();
            sembrados = camioneroRepository.count();
        }
        log.warn("Reset de datos de prueba en ms-camioneros: {} camioneros eliminados, {} camioneros de demo sembrados",
                eliminados, sembrados);
        return new ResetDatosPruebaResponse(eliminados, sembrados);
    }
}
