package utn.fc.backend.pedidos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utn.fc.backend.pedidos.config.DataSeeder;
import utn.fc.backend.pedidos.dto.ResetDatosPruebaResponse;
import utn.fc.backend.pedidos.repository.PedidoRepository;

/**
 * Reset de datos de prueba (solo para demo/testing): vacía las 4 colas regionales borrando TODOS los pedidos,
 * conservando intacto el catálogo geográfico (regiones, ciudades y heladerías).
 * <p>
 * Permite que la colección de Postman corra siempre sobre colas vacías, sin depender de lo que cargue el
 * {@link DataSeeder}. Con {@code resembrar = true} vuelve a cargar los pedidos de demo del DataSeeder.
 */
@Service
public class DatosPruebaService {

    private static final Logger log = LoggerFactory.getLogger(DatosPruebaService.class);

    private final PedidoRepository pedidoRepository;
    private final DataSeeder dataSeeder;

    public DatosPruebaService(PedidoRepository pedidoRepository, DataSeeder dataSeeder) {
        this.pedidoRepository = pedidoRepository;
        this.dataSeeder = dataSeeder;
    }

    @Transactional
    public ResetDatosPruebaResponse resetear(boolean resembrar) {
        long eliminados = pedidoRepository.count();
        pedidoRepository.deleteAllInBatch();

        long sembrados = 0;
        if (resembrar) {
            dataSeeder.recargarColasPendientesSiHaceFalta();
            sembrados = pedidoRepository.count();
        }
        log.warn("Reset de datos de prueba en ms-pedidos: {} pedidos eliminados, {} pedidos de demo sembrados",
                eliminados, sembrados);
        return new ResetDatosPruebaResponse(eliminados, sembrados);
    }
}
