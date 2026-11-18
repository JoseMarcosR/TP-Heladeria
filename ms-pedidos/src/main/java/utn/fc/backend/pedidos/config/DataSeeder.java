package utn.fc.backend.pedidos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.pedidos.model.Ciudad;
import utn.fc.backend.pedidos.model.EstadoPedido;
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
 * Catálogo geográfico inicial (4 regiones, ciudades de Córdoba y 37 heladerías con coordenadas reales)
 * más una tanda abundante de 75 pedidos PENDIENTE distribuidos en las 4 regiones para poder planificar
 * múltiples viajes seguidos por región desde el primer arranque.
 * <p>
 * Incluye auto-recarga inteligente: al iniciar ms-pedidos, revisa cuántos pedidos PENDIENTE quedan en
 * cada región; si alguna región bajó de 6 pedidos PENDIENTE (porque ya fueron despachados en viajes),
 * vuelve a poblar esa cola regional automáticamente.
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
        if (heladeriaRepository.count() < 35) {
            sembrarSucursalesExtra();
        }
        recargarColasPendientesSiHaceFalta();
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
    }

    private void sembrarSucursalesExtra() {
        List<Region> regiones = regionRepository.findAll();
        if (regiones.size() < 4) {
            return;
        }
        Region capital = regiones.get(0);
        Region noroeste = regiones.get(1);
        Region este = regiones.get(2);
        Region sur = regiones.get(3);

        // Extra CAPITAL (IDs 30..31)
        Ciudad unquillo = ciudad(capital, "Unquillo");
        heladeria(unquillo, "Heladería Unquillo Doble Avenida", "Av. San Martín 1500", "-31.231500", "-64.316000");
        Ciudad calera = ciudad(capital, "La Calera");
        heladeria(calera, "Heladería La Calera Centro", "Av. Vélez Sarsfield 550", "-31.343800", "-64.335200");

        // Extra NOROESTE (IDs 32..33)
        Ciudad capillaDelMonte = ciudad(noroeste, "Capilla del Monte");
        heladeria(capillaDelMonte, "Heladería Calle Techada Capilla", "Diagonal Buenos Aires 180", "-30.858300", "-64.525600");
        Ciudad minaClavero = ciudad(noroeste, "Mina Clavero");
        heladeria(minaClavero, "Heladería Mina Clavero Centro", "Av. San Martín 1100", "-31.722500", "-65.004200");

        // Extra ESTE (IDs 34..35)
        Ciudad oliva = ciudad(este, "Oliva");
        heladeria(oliva, "Heladería Oliva Ruta 9", "Bv. Rodolfo Moyano 320", "-32.041600", "-63.569800");
        Ciudad oncativo = ciudad(este, "Oncativo");
        heladeria(oncativo, "Heladería Oncativo Centro", "San Martín 740", "-31.912500", "-63.682200");

        // Extra SUR (IDs 36..37)
        Ciudad santaRosa = ciudad(sur, "Santa Rosa de Calamuchita");
        heladeria(santaRosa, "Heladería Costanera Santa Rosa", "Libertad 240", "-32.069000", "-64.536000");
        Ciudad almafuerte = ciudad(sur, "Almafuerte");
        heladeria(almafuerte, "Heladería Lago Piedras Moras", "Av. Buenos Aires 410", "-32.192000", "-64.255000");

        log.info("Catálogo geográfico ampliado: {} regiones, {} ciudades, {} heladerías",
                regionRepository.count(), ciudadRepository.count(), heladeriaRepository.count());
    }

    /**
     * Auto-recarga inteligente mirando SOLO los pedidos en estado PENDIENTE de cada región.
     * Si una región tiene menos de 6 pedidos PENDIENTE, le siembra una tanda completa para que
     * siempre se puedan planificar varios viajes seguidos al reiniciar ms-pedidos.
     * <p>
     * Es público porque también lo reutiliza {@code DatosPruebaService} para volver a cargar los pedidos de
     * demo después de un reset (por ejemplo, al terminar la colección de Postman).
     */
    public void recargarColasPendientesSiHaceFalta() {
        if (heladeriaRepository.count() < 29) {
            return;
        }

        // Región 1 - CAPITAL (19 pedidos = 1.205 tachos -> alcanza para 3 viajes completos + sobrante para 422)
        if (pedidoRepository.findCola(1L, EstadoPedido.PENDIENTE).size() < 6) {
            // Tanda 1 (Viaje 1 con camión de 350 tachos -> lleva los primeros 5 = 330 tachos)
            pedido(1L, 70);   // Nueva Córdoba
            pedido(2L, 60);   // Alta Córdoba
            pedido(3L, 70);   // Cerro de las Rosas
            pedido(4L, 65);   // Villa Allende
            pedido(18L, 65);  // Barrio Güemes
            // Tanda 2 (Viaje 2 con camión de 400 tachos -> lleva 6 pedidos = 375 tachos)
            pedido(19L, 60);  // Barrio General Paz
            pedido(20L, 60);  // Mendiolaza
            pedido(30L, 65);  // Unquillo
            pedido(31L, 65);  // La Calera
            pedido(1L, 65);   // Nueva Córdoba (refuerzo)
            pedido(2L, 60);   // Alta Córdoba (refuerzo)
            // Tanda 3 (Viaje 3 con camión de 450 tachos -> lleva 6 pedidos = 380 tachos)
            pedido(3L, 70);   // Cerro de las Rosas (refuerzo)
            pedido(18L, 60);  // Barrio Güemes (refuerzo)
            pedido(4L, 65);   // Villa Allende (refuerzo)
            pedido(19L, 65);  // Barrio General Paz (refuerzo)
            pedido(20L, 60);  // Mendiolaza (refuerzo)
            pedido(30L, 60);  // Unquillo (refuerzo)
            // Remanente intencional (2 pedidos = 120 tachos < 280) para demostrar 422 en el 4to intento
            pedido(31L, 60);  // La Calera (remanente)
            pedido(1L, 60);   // Nueva Córdoba (remanente)
            log.info("Cola PENDIENTE de Región 1 (CAPITAL) recargada");
        }

        // Región 2 - NOROESTE (18 pedidos = 1.145 tachos -> alcanza para 3 viajes completos + sobrante)
        if (pedidoRepository.findCola(2L, EstadoPedido.PENDIENTE).size() < 6) {
            pedido(5L, 70);   // Jesús María
            pedido(6L, 65);   // Villa Carlos Paz
            pedido(21L, 60);  // Cosquín
            pedido(22L, 60);  // La Falda
            pedido(23L, 55);  // Colonia Caroya
            pedido(7L, 75);   // Cruz del Eje
            pedido(8L, 60);   // Deán Funes
            pedido(32L, 65);  // Capilla del Monte
            pedido(33L, 70);  // Mina Clavero
            pedido(6L, 65);   // Villa Carlos Paz (refuerzo)
            pedido(21L, 65);  // Cosquín (refuerzo)
            pedido(22L, 60);  // La Falda (refuerzo)
            pedido(5L, 65);   // Jesús María (refuerzo)
            pedido(23L, 60);  // Colonia Caroya (refuerzo)
            pedido(32L, 65);  // Capilla del Monte (refuerzo)
            pedido(8L, 65);   // Deán Funes (refuerzo)
            pedido(7L, 60);   // Cruz del Eje (remanente)
            pedido(33L, 60);  // Mina Clavero (remanente)
            log.info("Cola PENDIENTE de Región 2 (NOROESTE) recargada");
        }

        // Región 3 - ESTE (20 pedidos = 1.240 tachos -> distancias largas para probar RN-02 y RN-03 10h)
        if (pedidoRepository.findCola(3L, EstadoPedido.PENDIENTE).size() < 6) {
            pedido(24L, 65);  // Arroyito
            pedido(9L, 70);   // San Francisco
            pedido(12L, 60);  // Las Varillas
            pedido(25L, 60);  // Bell Ville
            pedido(10L, 60);  // Marcos Juárez
            pedido(26L, 55);  // Brinkmann
            pedido(11L, 55);  // Morteros
            pedido(34L, 65);  // Oliva
            pedido(35L, 65);  // Oncativo
            pedido(9L, 60);   // San Francisco (refuerzo)
            pedido(24L, 65);  // Arroyito (refuerzo)
            pedido(12L, 65);  // Las Varillas (refuerzo)
            pedido(25L, 60);  // Bell Ville (refuerzo)
            pedido(10L, 65);  // Marcos Juárez (refuerzo)
            pedido(34L, 60);  // Oliva (refuerzo)
            pedido(35L, 60);  // Oncativo (refuerzo)
            pedido(26L, 65);  // Brinkmann (refuerzo)
            pedido(11L, 65);  // Morteros (refuerzo)
            pedido(24L, 60);  // Arroyito (remanente)
            pedido(9L, 60);   // San Francisco (remanente)
            log.info("Cola PENDIENTE de Región 3 (ESTE) recargada");
        }

        // Región 4 - SUR (18 pedidos = 1.145 tachos -> alcanza para 3 viajes completos + sobrante)
        if (pedidoRepository.findCola(4L, EstadoPedido.PENDIENTE).size() < 6) {
            pedido(27L, 65);  // Alta Gracia
            pedido(28L, 60);  // Villa General Belgrano
            pedido(16L, 65);  // Río Tercero
            pedido(15L, 70);  // Villa María
            pedido(13L, 60);  // Río Cuarto Centro
            pedido(14L, 60);  // Río Cuarto Banda Norte
            pedido(29L, 60);  // La Carlota
            pedido(36L, 65);  // Santa Rosa de Calamuchita
            pedido(37L, 65);  // Almafuerte
            pedido(17L, 70);  // Laboulaye
            pedido(27L, 60);  // Alta Gracia (refuerzo)
            pedido(28L, 65);  // Villa General Belgrano (refuerzo)
            pedido(16L, 65);  // Río Tercero (refuerzo)
            pedido(15L, 65);  // Villa María (refuerzo)
            pedido(13L, 65);  // Río Cuarto Centro (refuerzo)
            pedido(36L, 65);  // Santa Rosa (refuerzo)
            pedido(37L, 60);  // Almafuerte (remanente)
            pedido(14L, 60);  // Río Cuarto Banda Norte (remanente)
            log.info("Cola PENDIENTE de Región 4 (SUR) recargada");
        }

        log.info("Estado de pedidos iniciales listo (total en BD: {} pedidos)", pedidoRepository.count());
    }

    private Ciudad ciudad(Region region, String nombre) {
        return ciudadRepository.save(new Ciudad(region, nombre));
    }

    private void heladeria(Ciudad ciudad, String nombre, String direccion, String lat, String lng) {
        heladeriaRepository.save(new Heladeria(ciudad, nombre, direccion, new BigDecimal(lat), new BigDecimal(lng)));
    }

    private void pedido(Long idHeladeria, int cantidadTachos) {
        heladeriaRepository.findById(idHeladeria)
                .ifPresent(h -> pedidoRepository.save(new Pedido(h, cantidadTachos)));
    }
}