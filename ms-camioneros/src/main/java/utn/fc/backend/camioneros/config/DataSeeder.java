package utn.fc.backend.camioneros.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.camioneros.model.Camion;
import utn.fc.backend.camioneros.model.Camionero;
import utn.fc.backend.camioneros.repository.CamionRepository;
import utn.fc.backend.camioneros.repository.CamioneroRepository;

import java.time.Instant;

/**
 * Flota inicial de camioneros y camiones para poder probar la planificación (RN-04 y RN-02)
 * en las 4 regiones desde el primer arranque sin necesidad de cargarlos manualmente.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CamioneroRepository camioneroRepository;
    private final CamionRepository camionRepository;

    public DataSeeder(CamioneroRepository camioneroRepository, CamionRepository camionRepository) {
        this.camioneroRepository = camioneroRepository;
        this.camionRepository = camionRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (camioneroRepository.count() >= 5) {
            return;
        }

        Instant base = Instant.now().minusSeconds(3600);

        // 1) Capacidad 350 tachos (1ro en la cola FIFO global: ideal para probar corte RN-02)
        sembrar("30111222", "Juan Carlos Quiroga", "3515100001", true, "AF101AA", 350, base.plusSeconds(10));

        // 2) Capacidad 400 tachos (2do en la cola FIFO global)
        sembrar("31222333", "Martín Alejandro Sosa", "3515100002", true, "AF202BB", 400, base.plusSeconds(20));

        // 3) Capacidad 450 tachos (3ro en la cola FIFO global)
        sembrar("32333444", "Diego Fernando Cabrera", "3515100003", true, "AF303CC", 450, base.plusSeconds(30));

        // 4) Capacidad 500 tachos (4to en la cola: camión grande para probar corte de 10 h RN-03 en región ESTE)
        sembrar("33444555", "Lucas Gabriel Pereyra", "3515100004", true, "AF404DD", 500, base.plusSeconds(40));

        // 5) Capacidad 320 tachos (5to en la cola FIFO global)
        sembrar("34555666", "Roberto Nicolás Villarreal", "3515100005", true, "AG505EE", 320, base.plusSeconds(50));

        // 6) Capacidad 380 tachos (6to en la cola FIFO global)
        sembrar("35666777", "Sergio Emanuel Molina", "3515100006", true, "AG606FF", 380, base.plusSeconds(60));

        // 7) Contrato inactivo (demuestra que RN-04 lo excluye de la cola de asignación aunque sea más antiguo)
        sembrar("36777888", "Federico Andrés Ledesma (Sin Contrato)", "3515100007", false, "AG707GG", 420, base.plusSeconds(5));

        log.info("Flota inicial cargada: {} camioneros registrados en la cola global",
                camioneroRepository.count());
    }

    private void sembrar(String dni, String nombre, String telefono, boolean contratoActivo,
                         String patente, int capacidadMaxTachos, Instant ordenCola) {
        if (camioneroRepository.existsByDni(dni) || camionRepository.existsByPatente(patente)) {
            return;
        }
        Camion camion = new Camion(patente, capacidadMaxTachos);
        Camionero camionero = new Camionero(camion, dni, nombre, telefono, contratoActivo);
        camionero.setUltimaActualizacionCola(ordenCola);
        camioneroRepository.save(camionero);
    }
}