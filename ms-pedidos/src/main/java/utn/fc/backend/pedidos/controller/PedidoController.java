package utn.fc.backend.pedidos.controller;

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
import utn.fc.backend.pedidos.dto.EstadoPedidoRequest;
import utn.fc.backend.pedidos.dto.PedidoCreateRequest;
import utn.fc.backend.pedidos.dto.PedidoResponse;
import utn.fc.backend.pedidos.service.PedidoService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/pedidos")
@Tag(name = "Pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    @Operation(summary = "Registrar un nuevo pedido (RN-01: 40 a 80 tachos) y encolarlo en su región")
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody PedidoCreateRequest request) {
        PedidoResponse creado = pedidoService.crear(request);
        return ResponseEntity.created(URI.create("/v1/pedidos/" + creado.id())).body(creado);
    }

    @GetMapping("/regiones/{idRegion}/cola")
    @Operation(summary = "Consultar la cola secuencial de pedidos PENDIENTE de una región")
    public List<PedidoResponse> cola(@PathVariable Long idRegion) {
        return pedidoService.listarCola(idRegion);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el detalle, ubicación y estado de un pedido")
    public PedidoResponse obtener(@PathVariable Long id) {
        return pedidoService.obtener(id);
    }

    @PutMapping("/{id}/estado")
    @Operation(summary = "Modificar el estado del pedido; NO_ENTREGADO lo reencola como PENDIENTE")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoPedidoRequest request) {
        return pedidoService.cambiarEstado(id, request.estado());
    }

    /**
     * Endpoint INTERNO (solo ServicioInterno, no expuesto por el gateway): ms-logistica lo invoca para mandar al
     * final de la cola regional un pedido PENDIENTE rechazado por coordenadas inválidas (RN-05). Sin cuerpo.
     */
    @PutMapping("/{id}/reencolado")
    @Operation(summary = "[Interno] Mandar un pedido PENDIENTE al final de la cola de su región")
    public PedidoResponse reencolar(@PathVariable Long id) {
        return pedidoService.reencolarAlFinal(id);
    }
}
