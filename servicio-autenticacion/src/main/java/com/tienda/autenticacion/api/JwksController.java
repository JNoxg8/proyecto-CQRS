package com.tienda.autenticacion.api;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes ONLY the public key in the standard JWKS format (RFC 7517).
 * servicio-comandos and servicio-consultas download it to verify the signature of every token.
 */
@RestController
public class JwksController {

    private final RSAKey claveRsa;

    public JwksController(RSAKey claveRsa) {
        this.claveRsa = claveRsa;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(claveRsa.toPublicJWK()).toJSONObject();
    }
}
