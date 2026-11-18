package com.tienda.comandos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS: the browser blocks a page from localhost:8080 calling an API on localhost:8081 (different origin)
 * unless the API allows it. We allow ONLY the client apps' origin, not "*".
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] origenes;

    public CorsConfig(@Value("${tienda.cors-origenes}") String[] origenes) {
        this.origenes = origenes;
    }

    /** The write side only receives POST and PUT. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origenes)
                .allowedMethods("POST", "PUT");
    }
}
