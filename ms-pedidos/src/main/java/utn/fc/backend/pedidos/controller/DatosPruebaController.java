package utn.fc.backend.pedidos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import utn.fc.backend.pedidos.dto.ResetDatosPruebaResponse;
import utn.fc.backend.pedidos.service.DatosPruebaService;

/**
 * Endpoint administrativo de SOPORTE A PRUEBAS (no forma parte del contrato de negocio).
 * Solo rol Administrador. Se puede deshabilitar con DATOS_PRUEBA_RESET_HABILITADO=false (el endpoint deja de existir).
 */
@RestController
@RequestMapping("/v1/pedidos/admin")
@Tag(name = "Soporte a pruebas")
@ConditionalOnProperty(name = "app.datos-prueba.reset-habilitado", havingValue = "true", matchIfMissing = true)
public class DatosPruebaController {

    private final DatosPruebaService datosPruebaService;

    public DatosPruebaController(DatosPruebaService datosPruebaService) {
        this.datosPruebaService = datosPruebaService;
    }

    @PostMapping("/reset")
    @Operation(summary = "[Pruebas] Vaciar las colas regionales (borra todos los pedidos, conserva regiones y heladerías). "
            + "Con resembrar=true vuelve a cargar los pedidos de demo del DataSeeder")
    public ResetDatosPruebaResponse resetear(@RequestParam(defaultValue = "false") boolean resembrar) {
        return datosPruebaService.resetear(resembrar);
    }
}
