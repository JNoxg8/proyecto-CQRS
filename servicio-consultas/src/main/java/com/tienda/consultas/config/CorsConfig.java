package com.tienda.consultas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Only the client apps (localhost:8080) may call this API from a browser. */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origenes;

    public CorsConfig(@Value("${tienda.cors-origenes}") String[] origenes) {
        this.origenes = origenes;
    }

    /** Allows GET (queries, stream) and POST (rebuild) from the client apps. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origenes)
                .allowedMethods("GET", "POST");   // POST only for /api/admin/reconstruir
    }
}
