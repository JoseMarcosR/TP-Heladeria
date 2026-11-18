package utn.fc.backend.logistica.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import utn.fc.backend.logistica.service.DatosPruebaService;

import java.util.Map;

/**
 * Endpoint administrativo de SOPORTE A PRUEBAS (no forma parte del contrato de negocio).
 * Solo rol Administrador. Se puede deshabilitar con DATOS_PRUEBA_RESET_HABILITADO=false (el endpoint deja de existir).
 */
@RestController
@RequestMapping("/v1/viajes/admin")
@Tag(name = "Soporte a pruebas")
@ConditionalOnProperty(name = "app.datos-prueba.reset-habilitado", havingValue = "true", matchIfMissing = true)
public class DatosPruebaController {

    private final DatosPruebaService datosPruebaService;

    public DatosPruebaController(DatosPruebaService datosPruebaService) {
        this.datosPruebaService = datosPruebaService;
    }

    @PostMapping("/reset")
    @Operation(summary = "[Pruebas] Borrar todos los viajes y sus paradas (historial de hojas de ruta)")
    public Map<String, Long> resetear() {
        return Map.of("viajesEliminados", datosPruebaService.resetear());
    }
}
