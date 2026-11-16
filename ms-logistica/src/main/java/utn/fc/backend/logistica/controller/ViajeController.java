package utn.fc.backend.logistica.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import utn.fc.backend.logistica.dto.CierreViajeRequest;
import utn.fc.backend.logistica.dto.ViajeResponse;
import utn.fc.backend.logistica.service.PlanificadorService;
import utn.fc.backend.logistica.service.ViajeService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/viajes")
@Tag(name = "Viajes")
public class ViajeController {

    private final PlanificadorService planificadorService;
    private final ViajeService viajeService;

    public ViajeController(PlanificadorService planificadorService, ViajeService viajeService) {
        this.planificadorService = planificadorService;
        this.viajeService = viajeService;
    }

    @PostMapping("/planificar/regiones/{idRegion}")
    @Operation(summary = "Planificar la región: arma la hoja de ruta con rollback por capacidad (RN-02) y 10 h (RN-03)")
    public ResponseEntity<ViajeResponse> planificar(@PathVariable Long idRegion) {
        ViajeResponse viaje = planificadorService.planificar(idRegion);
        return ResponseEntity.created(URI.create("/v1/viajes/" + viaje.id())).body(viaje);
    }

    @GetMapping
    @Operation(summary = "Listar el historial de hojas de ruta")
    public List<ViajeResponse> listar() {
        return viajeService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el itinerario de un viaje y su enlace de navegación (RN-05)")
    public ViajeResponse obtener(@PathVariable Long id) {
        return viajeService.obtener(id);
    }

    @PutMapping("/{id}/cierre")
    @Operation(summary = "Cierre manual del viaje: asienta entregas/rechazos y libera al camionero (FUN-04)")
    public ViajeResponse cerrar(@PathVariable Long id, @Valid @RequestBody CierreViajeRequest request) {
        return viajeService.cerrar(id, request);
    }
}
