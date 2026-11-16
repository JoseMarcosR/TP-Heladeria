package utn.fc.backend.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;
import utn.fc.backend.gateway.security.KeycloakRealmRoleConverter;

/**
 * Seguridad perimetral: valida el JWT (firma, vigencia, issuer) y autoriza por rol cada endpoint
 * del contrato antes de enrutar. Sin token o token inválido => 401; token válido sin el rol => 403.
 * Todo lo que no figure en el contrato bajo /v1/** queda denegado.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    static final String ADMIN = "Administrador";
    static final String CAMIONERO = "Camionero";
    static final String SERVICIO = "ServicioInterno";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        // Documentación y health (no son recursos de negocio)
                        .pathMatchers("/actuator/health/**", "/swagger-ui.html", "/swagger-ui/**",
                                "/webjars/**", "/v3/api-docs/**", "/docs/**").permitAll()

                        // ms-pedidos
                        .pathMatchers(HttpMethod.GET, "/v1/regiones").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.POST, "/v1/pedidos").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.GET, "/v1/pedidos/regiones/*/cola").hasAnyRole(ADMIN, SERVICIO)
                        .pathMatchers(HttpMethod.GET, "/v1/pedidos/*").hasAnyRole(ADMIN, CAMIONERO)
                        .pathMatchers(HttpMethod.PUT, "/v1/pedidos/*/estado").hasAnyRole(ADMIN, SERVICIO)

                        // ms-camioneros
                        .pathMatchers(HttpMethod.POST, "/v1/camioneros").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.GET, "/v1/camioneros/siguiente").hasRole(SERVICIO)
                        .pathMatchers(HttpMethod.PUT, "/v1/camioneros/*/estado").hasAnyRole(ADMIN, SERVICIO)
                        .pathMatchers(HttpMethod.GET, "/v1/camiones/*").hasRole(SERVICIO)

                        // ms-logistica
                        .pathMatchers(HttpMethod.POST, "/v1/viajes/planificar/regiones/*").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.GET, "/v1/viajes").hasRole(ADMIN)
                        .pathMatchers(HttpMethod.GET, "/v1/viajes/*").hasAnyRole(ADMIN, CAMIONERO)
                        .pathMatchers(HttpMethod.PUT, "/v1/viajes/*/cierre").hasRole(ADMIN)

                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())))
                .build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
