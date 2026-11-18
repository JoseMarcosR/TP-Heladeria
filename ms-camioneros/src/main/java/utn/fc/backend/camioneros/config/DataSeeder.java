package utn.fc.backend.camioneros.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.camioneros.model.Camion;
import utn.fc.backend.camioneros.model.Camionero;
import utn.fc.backend.camioneros.model.EstadoCamionero;
import utn.fc.backend.camioneros.repository.CamionRepository;
import utn.fc.backend.camioneros.repository.CamioneroRepository;

import java.time.Instant;

/**
 * Flota inicial amplia de 16 camioneros y camiones para poder probar múltiples planificaciones seguidas
 * (RN-04 y RN-02) en las 4 regiones sin quedarse sin choferes.
 * <p>
 * Incluye auto-recarga inteligente: si al reiniciar ms-camioneros quedan menos de 4 camioneros DISPONIBLES
 * (porque quedaron EN_VIAJE de pruebas previas), los libera automáticamente a DISPONIBLE al final de la cola.
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
        sembrarFlotaDemo();

        // Auto-recarga: si al reiniciar quedaron menos de 4 camioneros DISPONIBLES, libera a los que estaban EN_VIAJE
        if (camioneroRepository.findCola(EstadoCamionero.DISPONIBLE, PageRequest.of(0, 10)).size() < 4) {
            Instant finCola = Instant.now();
            long offset = 1;
            for (Camionero c : camioneroRepository.findAll()) {
                if (c.isContratoActivo() && c.getEstado() == EstadoCamionero.EN_VIAJE) {
                    c.setEstado(EstadoCamionero.DISPONIBLE);
                    c.setUltimaActualizacionCola(finCola.plusSeconds(offset++));
                }
            }
            log.info("Auto-recarga de flota: camioneros EN_VIAJE liberados nuevamente a DISPONIBLE");
        }

        log.info("Flota inicial lista: {} camioneros registrados en total", camioneroRepository.count());
    }

    /**
     * Siembra los 16 camioneros de demo (los que ya existen por DNI/patente se saltean).
     * Es público porque también lo reutiliza {@code DatosPruebaService} para volver a cargar la flota de demo
     * después de un reset (por ejemplo, al terminar la colección de Postman).
     */
    public void sembrarFlotaDemo() {
        Instant base = Instant.now().minusSeconds(3600);

        // 1..6) Tanda original en orden de cola FIFO
        sembrar("30111222", "Juan Carlos Quiroga", "3515100001", true, "AF101AA", 350, base.plusSeconds(10));
        sembrar("31222333", "Martín Alejandro Sosa", "3515100002", true, "AF202BB", 400, base.plusSeconds(20));
        sembrar("32333444", "Diego Fernando Cabrera", "3515100003", true, "AF303CC", 450, base.plusSeconds(30));
        sembrar("33444555", "Lucas Gabriel Pereyra", "3515100004", true, "AF404DD", 500, base.plusSeconds(40));
        sembrar("34555666", "Roberto Nicolás Villarreal", "3515100005", true, "AG505EE", 320, base.plusSeconds(50));
        sembrar("35666777", "Sergio Emanuel Molina", "3515100006", true, "AG606FF", 380, base.plusSeconds(60));

        // 7) Contrato inactivo (demuestra que RN-04 lo excluye de la cola de asignación)
        sembrar("36777888", "Federico Andrés Ledesma (Sin Contrato)", "3515100007", false, "AG707GG", 420, base.plusSeconds(5));

        // 8..16) Flota adicional para soportar más de 12 viajes consecutivos sin cerrar viajes
        sembrar("37888999", "Gustavo Adrián Ferreyra", "3515100008", true, "AG808HH", 400, base.plusSeconds(70));
        sembrar("38999000", "Pablo Sebastián Moyano", "3515100009", true, "AH909II", 450, base.plusSeconds(80));
        sembrar("39101112", "Hernán Darío Ceballos", "3515100010", true, "AH110JJ", 360, base.plusSeconds(90));
        sembrar("40212223", "Matías Ezequiel Ludueña", "3515100011", true, "AH220KK", 420, base.plusSeconds(100));
        sembrar("41323334", "Claudio Rubén Bustos", "3515100012", true, "AI330LL", 480, base.plusSeconds(110));
        sembrar("42434445", "Gonzalo Javier Olmos", "3515100013", true, "AI440MM", 350, base.plusSeconds(120));
        sembrar("43545556", "Facundo Nahuel Zárate", "3515100014", true, "AI550NN", 440, base.plusSeconds(130));
        sembrar("44656667", "Rodrigo Maximiliano Paz", "3515100015", true, "AJ660OO", 500, base.plusSeconds(140));
        sembrar("45767778", "Leandro Agustín Peralta", "3515100016", true, "AJ770PP", 390, base.plusSeconds(150));
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