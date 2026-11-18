package com.tienda.comandos.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * WHO can send WHICH command. This is the real protection: hiding the admin page in the browser is only
 * cosmetic, because anyone can call the API directly (curl, Talend...).
 *
 * <pre>
 *  Command                              No token   CLIENTE                 ADMIN
 *  POST /api/pedidos (buy)               401        yes                     yes
 *  POST /api/pedidos/{id}/cancelar       401        only their own orders   yes
 *  POST /api/pedidos/{id}/despachar      401        403                     yes
 *  /api/calzados/** (inventory)          401        403                     yes
 * </pre>
 */
@Configuration
public class SeguridadConfig {

    /**
     * Stateless resource server: every request must carry a JWT signed by servicio-autenticacion
     * (verified with the public key from {@code jwk-set-uri}).
     */
    @Bean
    public SecurityFilterChain filtroSeguridad(HttpSecurity http) throws Exception {
        return http
                // No cookies -> no CSRF risk: the token travels in the Authorization header
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())   // lets the browser's CORS pre-flight (OPTIONS) through
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reglas -> reglas
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/pedidos").hasAnyRole("CLIENTE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/cancelar").hasAnyRole("CLIENTE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/despachar").hasRole("ADMIN")
                        .requestMatchers("/api/calzados/**").hasRole("ADMIN")
                        .anyRequest().denyAll())   // whitelist: whatever is not listed above is forbidden
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesDelToken())))
                .build();
    }

    /** Reads the "roles" claim of the token (["ADMIN"]) as Spring authorities ("ROLE_ADMIN"). */
    @Bean
    public JwtAuthenticationConverter rolesDelToken() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(roles);
        return conversor;
    }
}
