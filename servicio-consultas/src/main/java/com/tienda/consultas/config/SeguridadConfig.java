package com.tienda.consultas.config;

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
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * WHO can read WHAT.
 * <pre>
 *  Query                               No token        CLIENTE           ADMIN
 *  GET /api/catalogo/**                 yes (public)    yes               yes
 *  GET /api/stream (live)               shoes only      shoes + own       everything
 *                                                       orders
 *  GET /api/pedidos/mios                401             own orders        own orders
 *  GET /api/pedidos, /api/pedidos/{id}  401             403               yes
 *  POST /api/admin/reconstruir          401             403               yes
 * </pre>
 */
@Configuration
public class SeguridadConfig {

    @Bean
    public SecurityFilterChain filtroSeguridad(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reglas -> reglas
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/catalogo", "/api/catalogo/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/stream").permitAll()   // what you receive depends on who you are
                        .requestMatchers(HttpMethod.GET, "/api/pedidos/mios").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/pedidos", "/api/pedidos/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .bearerTokenResolver(tokenEnCabeceraOEnElStream())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolesDelToken())))
                .build();
    }

    /**
     * The browser's EventSource (SSE) cannot send headers, so ONLY for /api/stream the token may also come
     * as ?access_token=... (allowed by RFC 6750). Everywhere else it must travel in the Authorization header,
     * because URLs end up in browser history and server logs.
     */
    @Bean
    public BearerTokenResolver tokenEnCabeceraOEnElStream() {
        DefaultBearerTokenResolver soloCabecera = new DefaultBearerTokenResolver();
        DefaultBearerTokenResolver cabeceraOParametro = new DefaultBearerTokenResolver();
        cabeceraOParametro.setAllowUriQueryParameter(true);
        return peticion -> "/api/stream".equals(peticion.getRequestURI())
                ? cabeceraOParametro.resolve(peticion)
                : soloCabecera.resolve(peticion);
    }

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
