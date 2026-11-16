package utn.fc.backend.pedidos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.pedidos.model.Ciudad;
import utn.fc.backend.pedidos.model.Heladeria;
import utn.fc.backend.pedidos.model.Region;
import utn.fc.backend.pedidos.repository.CiudadRepository;
import utn.fc.backend.pedidos.repository.HeladeriaRepository;
import utn.fc.backend.pedidos.repository.RegionRepository;

import java.math.BigDecimal;

/**
 * Catálogo geográfico inicial (4 regiones, ciudades de Córdoba y heladerías con coordenadas),
 * para poder planificar rutas desde el primer arranque. Solo corre si la tabla de regiones está vacía.
 * <p>
 * Los nombres y direcciones de las heladerías son de ejemplo; las coordenadas son aproximadas
 * (centro de cada ciudad) y conviene reemplazarlas por datos reales.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RegionRepository regionRepository;
    private final CiudadRepository ciudadRepository;
    private final HeladeriaRepository heladeriaRepository;

    public DataSeeder(RegionRepository regionRepository,
                      CiudadRepository ciudadRepository,
                      HeladeriaRepository heladeriaRepository) {
        this.regionRepository = regionRepository;
        this.ciudadRepository = ciudadRepository;
        this.heladeriaRepository = heladeriaRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (regionRepository.count() > 0) {
            return;
        }
        Region capital = regionRepository.save(new Region("CAPITAL"));
        Region noroeste = regionRepository.save(new Region("NOROESTE"));
        Region este = regionRepository.save(new Region("ESTE"));
        Region sur = regionRepository.save(new Region("SUR"));

        // CAPITAL
        Ciudad cordoba = ciudad(capital, "Córdoba");
        heladeria(cordoba, "Heladería Nueva Córdoba", "Av. Hipólito Yrigoyen 450", "-31.423200", "-64.188400");
        heladeria(cordoba, "Heladería Alta Córdoba", "Av. Sabattini 1250", "-31.403000", "-64.179000");
        heladeria(cordoba, "Heladería Cerro de las Rosas", "Av. Rafael Núñez 4200", "-31.369000", "-64.233000");
        Ciudad villaAllende = ciudad(capital, "Villa Allende");
        heladeria(villaAllende, "Heladería Villa Allende Centro", "Av. Goycoechea 600", "-31.293000", "-64.295000");

        // NOROESTE
        Ciudad jesusMaria = ciudad(noroeste, "Jesús María");
        heladeria(jesusMaria, "Heladería Jesús María Centro", "Av. Tulio Del Bono 300", "-30.981000", "-64.094000");
        Ciudad carlosPaz = ciudad(noroeste, "Villa Carlos Paz");
        heladeria(carlosPaz, "Heladería Carlos Paz Costanera", "Av. Costanera 150", "-31.424100", "-64.497800");
        Ciudad cruzDelEje = ciudad(noroeste, "Cruz del Eje");
        heladeria(cruzDelEje, "Heladería Cruz del Eje", "Av. San Martín 500", "-30.727000", "-64.806000");
        Ciudad deanFunes = ciudad(noroeste, "Deán Funes");
        heladeria(deanFunes, "Heladería Deán Funes", "Av. Sarmiento 200", "-30.420000", "-64.350000");

        // ESTE
        Ciudad sanFrancisco = ciudad(este, "San Francisco");
        heladeria(sanFrancisco, "Heladería San Francisco Centro", "Bv. 9 de Julio 1100", "-31.428000", "-62.083000");
        Ciudad marcosJuarez = ciudad(este, "Marcos Juárez");
        heladeria(marcosJuarez, "Heladería Marcos Juárez", "Av. Lardizábal 400", "-32.699000", "-62.101000");
        Ciudad morteros = ciudad(este, "Morteros");
        heladeria(morteros, "Heladería Morteros", "Av. Hipólito Yrigoyen 300", "-30.710000", "-61.998000");
        Ciudad lasVarillas = ciudad(este, "Las Varillas");
        heladeria(lasVarillas, "Heladería Las Varillas", "Av. Sarmiento 250", "-31.872000", "-62.724000");

        // SUR
        Ciudad rioCuarto = ciudad(sur, "Río Cuarto");
        heladeria(rioCuarto, "Heladería Río Cuarto Centro", "Av. Sabattini 800", "-33.123200", "-64.349300");
        heladeria(rioCuarto, "Heladería Río Cuarto Banda Norte", "Av. España 1500", "-33.110000", "-64.356000");
        Ciudad villaMaria = ciudad(sur, "Villa María");
        heladeria(villaMaria, "Heladería Villa María Centro", "Bv. Sarmiento 600", "-32.407500", "-63.240300");
        Ciudad rioTercero = ciudad(sur, "Río Tercero");
        heladeria(rioTercero, "Heladería Río Tercero", "Av. Savio 700", "-32.173000", "-64.113000");
        Ciudad laboulaye = ciudad(sur, "Laboulaye");
        heladeria(laboulaye, "Heladería Laboulaye", "Av. Independencia 350", "-34.127000", "-63.390000");

        log.info("Catálogo geográfico inicial cargado: {} regiones, {} ciudades, {} heladerías",
                regionRepository.count(), ciudadRepository.count(), heladeriaRepository.count());
    }

    private Ciudad ciudad(Region region, String nombre) {
        return ciudadRepository.save(new Ciudad(region, nombre));
    }

    private void heladeria(Ciudad ciudad, String nombre, String direccion, String lat, String lng) {
        heladeriaRepository.save(new Heladeria(ciudad, nombre, direccion, new BigDecimal(lat), new BigDecimal(lng)));
    }
}
