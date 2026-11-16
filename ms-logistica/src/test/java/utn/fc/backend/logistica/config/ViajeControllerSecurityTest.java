package utn.fc.backend.logistica.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import utn.fc.backend.logistica.controller.ViajeController;
import utn.fc.backend.logistica.dto.ViajeResponse;
import utn.fc.backend.logistica.model.EstadoViaje;
import utn.fc.backend.logistica.service.PlanificadorService;
import utn.fc.backend.logistica.service.ViajeService;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seguridad de ms-logistica con MockMvc y sin base de datos: los servicios están mockeados y el JWT se simula
 * con spring-security-test (se verifica la autorización por rol; la firma del token la valida Spring con Keycloak).
 */
@WebMvcTest(ViajeController.class)
@Import(SecurityConfig.class)
class ViajeControllerSecurityTest {

    /** Cliente HTTP simulado para invocar el controller con los filtros de seguridad. */
    @Autowired
    MockMvc mvc;

    /** Beans mockeados: servicios del controller y JwtDecoder (evita contactar a Keycloak). */
    @MockBean
    PlanificadorService planificadorService;
    @MockBean
    ViajeService viajeService;
    @MockBean
    JwtDecoder jwtDecoder;

    /** Respuesta de viaje de ejemplo que devuelven los servicios mockeados. */
    private static ViajeResponse viaje() {
        return new ViajeResponse(1L, 1L, 7L, 300, 24_000, "https://www.google.com/maps/dir//-31.5,-64.1",
                EstadoViaje.PLANIFICADO, Instant.now(), null, List.of());
    }

    /** Simula un JWT autenticado con el rol indicado (ROLE_ + rol). */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor como(String rol) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    /** Planificar sin token: 401 y el servicio no se invoca. */
    @Test
    void planificarSinTokenDa401() throws Exception {
        mvc.perform(post("/v1/viajes/planificar/regiones/1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(planificadorService);
    }

    /** Planificar con rol Camionero: 403 y el servicio no se invoca. */
    @Test
    void planificarConRolCamioneroDa403() throws Exception {
        mvc.perform(post("/v1/viajes/planificar/regiones/1").with(como("Camionero")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(planificadorService);
    }

    /** Planificar con rol Administrador: 201 Created. */
    @Test
    void planificarConRolAdministradorDa201() throws Exception {
        when(planificadorService.planificar(1L)).thenReturn(viaje());

        mvc.perform(post("/v1/viajes/planificar/regiones/1").with(como("Administrador")))
                .andExpect(status().isCreated());
    }

    /** Un Camionero puede consultar un viaje por id: 200 OK. */
    @Test
    void camioneroPuedeConsultarUnViaje() throws Exception {
        when(viajeService.obtener(1L)).thenReturn(viaje());

        mvc.perform(get("/v1/viajes/1").with(como("Camionero")))
                .andExpect(status().isOk());
    }

    /** Un Camionero no puede listar todos los viajes: 403. */
    @Test
    void camioneroNoPuedeListarViajes() throws Exception {
        mvc.perform(get("/v1/viajes").with(como("Camionero")))
                .andExpect(status().isForbidden());
    }

    /** Un Camionero no puede cerrar viajes: 403 y el servicio no se invoca. */
    @Test
    void camioneroNoPuedeCerrarViajes() throws Exception {
        mvc.perform(put("/v1/viajes/1/cierre").with(como("Camionero"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultados\":[{\"idPedido\":1,\"estado\":\"ENTREGADO\"}]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(viajeService);
    }

    /** El rol ServicioInterno no accede a los endpoints de viajes: 403. */
    @Test
    void servicioInternoNoTieneAccesoALosViajes() throws Exception {
        mvc.perform(get("/v1/viajes").with(como("ServicioInterno")))
                .andExpect(status().isForbidden());
    }

    /** Cierre con cuerpo vacío (sin resultados): 400 por validación. */
    @Test
    void cierreConCuerpoInvalidoDa400() throws Exception {
        mvc.perform(put("/v1/viajes/1/cierre").with(como("Administrador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    /** Cierre con un estado de entrega inexistente: 400 Bad Request. */
    @Test
    void cierreConEstadoInexistenteDa400() throws Exception {
        mvc.perform(put("/v1/viajes/1/cierre").with(como("Administrador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultados\":[{\"idPedido\":1,\"estado\":\"PERDIDO\"}]}"))
                .andExpect(status().isBadRequest());
    }
}
