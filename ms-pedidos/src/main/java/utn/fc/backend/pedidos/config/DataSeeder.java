package utn.fc.backend.pedidos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.pedidos.model.Ciudad;
import utn.fc.backend.pedidos.model.Heladeria;
import utn.fc.backend.pedidos.model.Pedido;
import utn.fc.backend.pedidos.model.Region;
import utn.fc.backend.pedidos.repository.CiudadRepository;
import utn.fc.backend.pedidos.repository.HeladeriaRepository;
import utn.fc.backend.pedidos.repository.PedidoRepository;
import utn.fc.backend.pedidos.repository.RegionRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Catálogo geográfico inicial (4 regiones, ciudades de Córdoba y heladerías con coordenadas reales)
 * más una tanda inicial de pedidos PENDIENTE en las 4 regiones para poder probar la planificación
 * desde el primer arranque.
 * <p>
 * Las primeras 17 heladerías (IDs 1..17) conservan su orden original para mantener compatibilidad
 * con la colección de Postman; las adicionales se agregan a continuación (IDs 18..29).
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RegionRepository regionRepository;
    private final CiudadRepository ciudadRepository;
    private final HeladeriaRepository heladeriaRepository;
    private final PedidoRepository pedidoRepository;

    public DataSeeder(RegionRepository regionRepository,
                      CiudadRepository ciudadRepository,
                      HeladeriaRepository heladeriaRepository,
                      PedidoRepository pedidoRepository) {
        this.regionRepository = regionRepository;
        this.ciudadRepository = ciudadRepository;
        this.heladeriaRepository = heladeriaRepository;
        this.pedidoRepository = pedidoRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (regionRepository.count() == 0) {
            sembrarCatalogoBase();
        }
        if (heladeriaRepository.count() < 20) {
            sembrarSucursalesAdicionales();
        }
        if (pedidoRepository.count() < 5) {
            sembrarPedidosIniciales();
        }
    }

    private void sembrarCatalogoBase() {
        Region capital = regionRepository.save(new Region("CAPITAL"));
        Region noroeste = regionRepository.save(new Region("NOROESTE"));
        Region este = regionRepository.save(new Region("ESTE"));
        Region sur = regionRepository.save(new Region("SUR"));

        // =====================================================================
        // Bloque original (IDs 1..17) — conserva orden para colección Postman
        // =====================================================================

        // CAPITAL (IDs 1..4)
        Ciudad cordoba = ciudad(capital, "Córdoba");
        heladeria(cordoba, "Heladería Nueva Córdoba", "Av. Hipólito Yrigoyen 450", "-31.423200", "-64.188400");
        heladeria(cordoba, "Heladería Alta Córdoba", "Av. Sabattini 1250", "-31.403000", "-64.179000");
        heladeria(cordoba, "Heladería Cerro de las Rosas", "Av. Rafael Núñez 4200", "-31.369000", "-64.233000");
        Ciudad villaAllende = ciudad(capital, "Villa Allende");
        heladeria(villaAllende, "Heladería Villa Allende Centro", "Av. Goycoechea 600", "-31.293000", "-64.295000");

        // NOROESTE (IDs 5..8)
        Ciudad jesusMaria = ciudad(noroeste, "Jesús María");
        heladeria(jesusMaria, "Heladería Jesús María Centro", "Av. Tulio Del Bono 300", "-30.981000", "-64.094000");
        Ciudad carlosPaz = ciudad(noroeste, "Villa Carlos Paz");
        heladeria(carlosPaz, "Heladería Carlos Paz Costanera", "Av. Costanera 150", "-31.424100", "-64.497800");
        Ciudad cruzDelEje = ciudad(noroeste, "Cruz del Eje");
        heladeria(cruzDelEje, "Heladería Cruz del Eje", "Av. San Martín 500", "-30.727000", "-64.806000");
        Ciudad deanFunes = ciudad(noroeste, "Deán Funes");
        heladeria(deanFunes, "Heladería Deán Funes", "Av. Sarmiento 200", "-30.420000", "-64.350000");

        // ESTE (IDs 9..12)
        Ciudad sanFrancisco = ciudad(este, "San Francisco");
        heladeria(sanFrancisco, "Heladería San Francisco Centro", "Bv. 9 de Julio 1100", "-31.428000", "-62.083000");
        Ciudad marcosJuarez = ciudad(este, "Marcos Juárez");
        heladeria(marcosJuarez, "Heladería Marcos Juárez", "Av. Lardizábal 400", "-32.699000", "-62.101000");
        Ciudad morteros = ciudad(este, "Morteros");
        heladeria(morteros, "Heladería Morteros", "Av. Hipólito Yrigoyen 300", "-30.710000", "-61.998000");
        Ciudad lasVarillas = ciudad(este, "Las Varillas");
        heladeria(lasVarillas, "Heladería Las Varillas", "Av. Sarmiento 250", "-31.872000", "-62.724000");

        // SUR (IDs 13..17)
        Ciudad rioCuarto = ciudad(sur, "Río Cuarto");
        heladeria(rioCuarto, "Heladería Río Cuarto Centro", "Av. Sabattini 800", "-33.123200", "-64.349300");
        heladeria(rioCuarto, "Heladería Río Cuarto Banda Norte", "Av. España 1500", "-33.110000", "-64.356000");
        Ciudad villaMaria = ciudad(sur, "Villa María");
        heladeria(villaMaria, "Heladería Villa María Centro", "Bv. Sarmiento 600", "-32.407500", "-63.240300");
        Ciudad rioTercero = ciudad(sur, "Río Tercero");
        heladeria(rioTercero, "Heladería Río Tercero", "Av. Savio 700", "-32.173000", "-64.113000");
        Ciudad laboulaye = ciudad(sur, "Laboulaye");
        heladeria(laboulaye, "Heladería Laboulaye", "Av. Independencia 350", "-34.127000", "-63.390000");
    }

    private void sembrarSucursalesAdicionales() {
        List<Region> regiones = regionRepository.findAll();
        if (regiones.size() < 4) {
            return;
        }
        Region capital = regiones.get(0);
        Region noroeste = regiones.get(1);
        Region este = regiones.get(2);
        Region sur = regiones.get(3);

        // Más CAPITAL (IDs 18..20)
        Ciudad cordobaBarrios = ciudad(capital, "Córdoba Centro / Barrios");
        heladeria(cordobaBarrios, "Heladería Barrio Güemes", "Belgrano 750", "-31.425800", "-64.193200");
        heladeria(cordobaBarrios, "Heladería Barrio General Paz", "24 de Septiembre 1400", "-31.414500", "-64.167200");
        Ciudad mendiolaza = ciudad(capital, "Mendiolaza");
        heladeria(mendiolaza, "Heladería Mendiolaza Centro", "Av. Tissera 1200", "-31.267500", "-64.299500");

        // Más NOROESTE (IDs 21..23)
        Ciudad cosquin = ciudad(noroeste, "Cosquín");
        heladeria(cosquin, "Heladería Plaza Próspero Molina", "Av. San Martín 680", "-31.244200", "-64.465800");
        Ciudad laFalda = ciudad(noroeste, "La Falda");
        heladeria(laFalda, "Heladería Edén La Falda", "Av. Edén 450", "-31.092100", "-64.482000");
        Ciudad coloniaCaroya = ciudad(noroeste, "Colonia Caroya");
        heladeria(coloniaCaroya, "Heladería Av. San Martín Caroya", "Av. San Martín 2100", "-31.021000", "-64.062000");

        // Más ESTE (IDs 24..26)
        Ciudad arroyito = ciudad(este, "Arroyito");
        heladeria(arroyito, "Heladería Arroyito Ruta 19", "Av. Pagani 850", "-31.419000", "-63.050000");
        Ciudad bellVille = ciudad(este, "Bell Ville");
        heladeria(bellVille, "Heladería Bell Ville Centro", "Córdoba 520", "-32.626000", "-62.689000");
        Ciudad brinkmann = ciudad(este, "Brinkmann");
        heladeria(brinkmann, "Heladería Brinkmann Centro", "Bv. Belgrano 620", "-30.866500", "-62.038000");

        // Más SUR (IDs 27..29)
        Ciudad altaGracia = ciudad(sur, "Alta Gracia");
        heladeria(altaGracia, "Heladería Tajamar Alta Gracia", "Av. del Libertador 350", "-31.658000", "-64.428000");
        Ciudad villaGralBelgrano = ciudad(sur, "Villa General Belgrano");
        heladeria(villaGralBelgrano, "Heladería Centro Alpino VGB", "Av. Julio A. Roca 180", "-31.976500", "-64.558500");
        Ciudad carlota = ciudad(sur, "La Carlota");
        heladeria(carlota, "Heladería La Carlota Centro", "Av. Vélez Sarsfield 410", "-33.419000", "-63.297000");

        log.info("Catálogo geográfico inicial cargado: {} regiones, {} ciudades, {} heladerías",
                regionRepository.count(), ciudadRepository.count(), heladeriaRepository.count());
    }

    private void sembrarPedidosIniciales() {
        List<Heladeria> todas = heladeriaRepository.findAll();
        if (todas.size() < 17) {
            return;
        }

        // Región 1 - CAPITAL (7 pedidos = 450 tachos: prueba corte de capacidad en camiones de 350 o 400)
        pedido(1L, 70);   // Nueva Córdoba
        pedido(2L, 60);   // Alta Córdoba
        pedido(3L, 70);   // Cerro de las Rosas
        pedido(4L, 65);   // Villa Allende
        pedido(18L, 65);  // Barrio Güemes
        pedido(19L, 60);  // Barrio General Paz
        pedido(20L, 60);  // Mendiolaza

        // Región 2 - NOROESTE (7 pedidos = 445 tachos)
        pedido(5L, 70);   // Jesús María
        pedido(6L, 65);   // Villa Carlos Paz
        pedido(21L, 60);  // Cosquín
        pedido(22L, 60);  // La Falda
        pedido(23L, 55);  // Colonia Caroya
        pedido(7L, 75);   // Cruz del Eje
        pedido(8L, 60);   // Deán Funes

        // Región 3 - ESTE (8 pedidos = 480 tachos: distancias largas para probar capacidad y cadena de frío)
        pedido(24L, 65);  // Arroyito
        pedido(9L, 70);   // San Francisco
        pedido(12L, 60);  // Las Varillas
        pedido(25L, 60);  // Bell Ville
        pedido(10L, 60);  // Marcos Juárez
        pedido(26L, 55);  // Brinkmann
        pedido(11L, 55);  // Morteros
        pedido(9L, 55);   // San Francisco (2do pedido)

        // Región 4 - SUR (7 pedidos = 440 tachos)
        pedido(27L, 65);  // Alta Gracia
        pedido(28L, 60);  // Villa General Belgrano
        pedido(16L, 65);  // Río Tercero
        pedido(15L, 70);  // Villa María
        pedido(13L, 60);  // Río Cuarto Centro
        pedido(14L, 60);  // Río Cuarto Banda Norte
        pedido(29L, 60);  // La Carlota

        log.info("Pedidos iniciales PENDIENTE sembrados: {} pedidos en total en las 4 regiones",
                pedidoRepository.count());
    }

    private Ciudad ciudad(Region region, String nombre) {
        return ciudadRepository.save(new Ciudad(region, nombre));
    }

    private Heladeria heladeria(Ciudad ciudad, String nombre, String direccion, String lat, String lng) {
        return heladeriaRepository.save(
                new Heladeria(ciudad, nombre, direccion, new BigDecimal(lat), new BigDecimal(lng)));
    }

    private void pedido(Long idHeladeria, int cantidadTachos) {
        heladeriaRepository.findById(idHeladeria)
                .ifPresent(h -> pedidoRepository.save(new Pedido(h, cantidadTachos)));
    }
}