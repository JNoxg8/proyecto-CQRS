package com.tienda.autenticacion.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * RSA key pair used to sign the tokens (RS256 = asymmetric):
 * <ul>
 *   <li>PRIVATE key: stays here, only this service can SIGN tokens.</li>
 *   <li>PUBLIC key: published at /.well-known/jwks.json, the other services use it to VERIFY tokens.</li>
 * </ul>
 * With a shared secret (HS256) every service that verifies could also forge tokens; with RS256 it cannot.
 *
 * <p>The pair is generated on every start (simple for a class project): restarting this service
 * invalidates the old tokens, although not instantly: the other services cache the public keys for a few
 * minutes, so an old token keeps working until that cache expires. That is a general property of JWTs:
 * they are verified locally, so they cannot be revoked at once (hence short expiration times).
 * Production systems load the key from a vault instead of generating it.
 */
@Configuration
public class ClavesConfig {

    @Bean
    public RSAKey claveRsa() throws NoSuchAlgorithmException {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        KeyPair par = generador.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) par.getPublic())
                .privateKey(par.getPrivate())
                .keyID(UUID.randomUUID().toString())   // "kid": tells verifiers which key signed the token
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey claveRsa) {
        JWKSource<SecurityContext> fuente = new ImmutableJWKSet<>(new JWKSet(claveRsa));
        return new NimbusJwtEncoder(fuente);
    }
}
