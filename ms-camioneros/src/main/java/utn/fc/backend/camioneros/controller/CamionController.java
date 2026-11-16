package utn.fc.backend.camioneros.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import utn.fc.backend.camioneros.dto.CamionResponse;
import utn.fc.backend.camioneros.service.CamioneroService;

@RestController
@RequestMapping("/v1/camiones")
@Tag(name = "Camiones")
public class CamionController {

    private final CamioneroService camioneroService;

    public CamionController(CamioneroService camioneroService) {
        this.camioneroService = camioneroService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar especificaciones y capacidad de freezer de un camión (RN-02)")
    public CamionResponse obtener(@PathVariable Long id) {
        return camioneroService.obtenerCamion(id);
    }
}
