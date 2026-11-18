package utn.fc.backend.camioneros.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Cada microservicio valida el JWT de forma autónoma (sin librería común, según el contrato).
 * Los roles de Keycloak se leen de realm_access.roles y se exponen como ROLE_<nombre>.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String ADMIN = "Administrador";
    static final String SERVICIO = "ServicioInterno";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/camioneros").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.GET, "/v1/camioneros/siguiente").hasRole(SERVICIO)
                        .requestMatchers(HttpMethod.PUT, "/v1/camioneros/*/estado").hasAnyRole(ADMIN, SERVICIO)
                        .requestMatchers(HttpMethod.GET, "/v1/camiones/*").hasRole(SERVICIO)
                        // Soporte a pruebas (Postman): vaciar/re-sembrar la flota. Solo Administrador.
                        .requestMatchers(HttpMethod.POST, "/v1/camioneros/admin/reset").hasRole(ADMIN)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }

    static class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
        @Override
        public Collection<GrantedAuthority> convert(Jwt jwt) {
            Object realmAccess = jwt.getClaims().get("realm_access");
            if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
                return roles.stream()
                        .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                        .toList();
            }
            return List.of();
        }
    }
}
