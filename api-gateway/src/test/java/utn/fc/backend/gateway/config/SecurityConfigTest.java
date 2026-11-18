package utn.fc.backend.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import utn.fc.backend.gateway.security.KeycloakRealmRoleConverter;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verificación de la seguridad perimetral del API Gateway (ESC-06 / CV-06):
 * 401 sin token, 403 cuando el rol no está autorizado, 200 cuando el rol corresponde,
 * y denegación de rutas internas o no expuestas en el contrato.
 */
@WebFluxTest(controllers = SecurityConfigTest.DummyDownstreamController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    WebTestClient webClient;

    @MockBean
    ReactiveJwtDecoder jwtDecoder;

    /** Stub de los endpoints enrutados para verificar que el filtro deja pasar o bloquea cada ruta. */
    @TestConfiguration
    @RestController
    static class DummyDownstreamController {
        @GetMapping("/v1/regiones")
        String regiones() { return "ok"; }

        @PostMapping("/v1/pedidos")
        String crearPedido() { return "ok"; }

        @GetMapping("/v1/pedidos/{id}")
        String obtenerPedido(@PathVariable String id) { return "ok"; }

        @GetMapping("/v1/pedidos/regiones/{id}/cola")
        String colaRegional(@PathVariable String id) { return "ok"; }

        @PutMapping("/v1/pedidos/{id}/reencolado")
        String reencolar(@PathVariable String id) { return "ok"; }

        @PostMapping("/v1/camioneros")
        String crearCamionero() { return "ok"; }

        @GetMapping("/v1/camioneros/siguiente")
        String siguienteCamionero() { return "ok"; }

        @PostMapping("/v1/viajes/planificar/regiones/{id}")
        String planificar(@PathVariable String id) { return "ok"; }

        @GetMapping("/v1/viajes")
        String listarViajes() { return "ok"; }

        @GetMapping("/v1/viajes/{id}")
        String obtenerViaje(@PathVariable String id) { return "ok"; }

        @PutMapping("/v1/viajes/{id}/cierre")
        String cerrarViaje(@PathVariable String id) { return "ok"; }

        @PostMapping({"/v1/pedidos/admin/reset", "/v1/camioneros/admin/reset", "/v1/viajes/admin/reset"})
        String resetDatosPrueba() { return "ok"; }
    }

    private static SecurityMockServerConfigurers.JwtMutator como(String rol) {
        return SecurityMockServerConfigurers.mockJwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void peticionSinTokenDevuelve401() {
        webClient.get().uri("/v1/regiones")
                .exchange()
                .expectStatus().isUnauthorized();

        webClient.post().uri("/v1/viajes/planificar/regiones/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void camioneroNoPuedePlanificarNiCrearPedidosNiCerrarViajes_403() {
        webClient.mutateWith(como("Camionero"))
                .post().uri("/v1/viajes/planificar/regiones/1")
                .exchange()
                .expectStatus().isForbidden();

        webClient.mutateWith(como("Camionero"))
                .post().uri("/v1/pedidos")
                .exchange()
                .expectStatus().isForbidden();

        webClient.mutateWith(como("Camionero"))
                .put().uri("/v1/viajes/1/cierre")
                .exchange()
                .expectStatus().isForbidden();

        webClient.mutateWith(como("Camionero"))
                .get().uri("/v1/regiones")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void camioneroPuedeConsultarDetalleDePedidoYViaje_200() {
        webClient.mutateWith(como("Camionero"))
                .get().uri("/v1/pedidos/10")
                .exchange()
                .expectStatus().isOk();

        webClient.mutateWith(como("Camionero"))
                .get().uri("/v1/viajes/5")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void administradorAccedeAGestionYPlanificacion_200() {
        webClient.mutateWith(como("Administrador"))
                .get().uri("/v1/regiones")
                .exchange()
                .expectStatus().isOk();

        webClient.mutateWith(como("Administrador"))
                .post().uri("/v1/pedidos")
                .exchange()
                .expectStatus().isOk();

        webClient.mutateWith(como("Administrador"))
                .post().uri("/v1/camioneros")
                .exchange()
                .expectStatus().isOk();

        webClient.mutateWith(como("Administrador"))
                .post().uri("/v1/viajes/planificar/regiones/1")
                .exchange()
                .expectStatus().isOk();

        webClient.mutateWith(como("Administrador"))
                .put().uri("/v1/viajes/1/cierre")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void endpointsExclusivosDeServicioInternoRechazanAdministradorYCamionero() {
        webClient.mutateWith(como("Administrador"))
                .get().uri("/v1/camioneros/siguiente")
                .exchange()
                .expectStatus().isForbidden();

        webClient.mutateWith(como("ServicioInterno"))
                .get().uri("/v1/camioneros/siguiente")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void endpointInternoReencoladoQuedaBloqueadoEnGatewayInclusoConToken() {
        webClient.mutateWith(como("Administrador"))
                .put().uri("/v1/pedidos/1/reencolado")
                .exchange()
                .expectStatus().isForbidden();

        webClient.mutateWith(como("ServicioInterno"))
                .put().uri("/v1/pedidos/1/reencolado")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void resetDeDatosDePruebaSoloParaAdministrador() {
        for (String uri : new String[]{"/v1/pedidos/admin/reset", "/v1/camioneros/admin/reset", "/v1/viajes/admin/reset"}) {
            webClient.post().uri(uri)
                    .exchange()
                    .expectStatus().isUnauthorized();

            webClient.mutateWith(como("Camionero"))
                    .post().uri(uri)
                    .exchange()
                    .expectStatus().isForbidden();

            webClient.mutateWith(como("ServicioInterno"))
                    .post().uri(uri)
                    .exchange()
                    .expectStatus().isForbidden();

            webClient.mutateWith(como("Administrador"))
                    .post().uri(uri)
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Test
    void keycloakRealmRoleConverterExtraeRolesDeRealmAccess() {
        KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();
        Jwt jwt = new Jwt(
                "token-valor",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("realm_access", Map.of("roles", List.of("Administrador", "Camionero")))
        );

        Collection<GrantedAuthority> authorities = converter.convert(jwt);
        List<String> nombres = authorities.stream().map(GrantedAuthority::getAuthority).toList();

        assertEquals(List.of("ROLE_Administrador", "ROLE_Camionero"), nombres);
    }

    @Test
    void keycloakRealmRoleConverterSinClaimDevuelveListaVacia() {
        KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();
        Jwt jwt = new Jwt(
                "token-valor",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of("sub", "usuario-sin-roles")
        );

        assertTrue(converter.convert(jwt).isEmpty());
    }
}
