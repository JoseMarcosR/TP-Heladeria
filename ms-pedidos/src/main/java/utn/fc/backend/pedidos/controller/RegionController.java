package utn.fc.backend.pedidos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import utn.fc.backend.pedidos.dto.RegionResponse;
import utn.fc.backend.pedidos.service.PedidoService;

import java.util.List;

@RestController
@RequestMapping("/v1/regiones")
@Tag(name = "Regiones")
public class RegionController {

    private final PedidoService pedidoService;

    public RegionController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    @Operation(summary = "Listar las regiones disponibles (CAPITAL, NOROESTE, ESTE, SUR)")
    public List<RegionResponse> listar() {
        return pedidoService.listarRegiones();
    }
}
