package utn.fc.backend.camioneros.controller;

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
import utn.fc.backend.camioneros.dto.CamioneroCreateRequest;
import utn.fc.backend.camioneros.dto.CamioneroResponse;
import utn.fc.backend.camioneros.dto.EstadoCamioneroRequest;
import utn.fc.backend.camioneros.service.CamioneroService;

import java.net.URI;

@RestController
@RequestMapping("/v1/camioneros")
@Tag(name = "Camioneros")
public class CamioneroController {

    private final CamioneroService camioneroService;

    public CamioneroController(CamioneroService camioneroService) {
        this.camioneroService = camioneroService;
    }

    @PostMapping
    @Operation(summary = "Inscribir un camionero y asociarlo a un camión con su capacidad máxima (hasta 500 tachos)")
    public ResponseEntity<CamioneroResponse> registrar(@Valid @RequestBody CamioneroCreateRequest request) {
        CamioneroResponse creado = camioneroService.registrar(request);
        return ResponseEntity.created(URI.create("/v1/camioneros/" + creado.id())).body(creado);
    }

    @GetMapping("/siguiente")
    @Operation(summary = "Obtener el próximo camionero de la cola global FIFO (RN-04)")
    public CamioneroResponse siguiente() {
        return camioneroService.siguiente();
    }

    @PutMapping("/{id}/estado")
    @Operation(summary = "Actualizar la disponibilidad (EN_VIAJE al iniciar ruta, DISPONIBLE al finalizar)")
    public CamioneroResponse cambiarEstado(@PathVariable Long id,
                                           @Valid @RequestBody EstadoCamioneroRequest request) {
        return camioneroService.cambiarEstado(id, request.estado());
    }
}
