package com.tienda.autenticacion.token;

import com.tienda.autenticacion.api.RespuestaSesion;
import com.tienda.autenticacion.dominio.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Builds the JWT. Its payload (readable by anyone, but impossible to change without breaking the signature):
 * <pre>
 * { "iss": "tienda-autenticacion", "sub": "ana@correo.com", "nombre": "Ana Rojas",
 *   "roles": ["CLIENTE"], "iat": 1731850000, "exp": 1731857200 }
 * </pre>
 * Never put secrets inside a JWT: it is signed, not encrypted.
 */
@Service
public class TokenServicio {

    public static final String EMISOR = "tienda-autenticacion";

    private final JwtEncoder encoder;
    private final Duration duracion;

    public TokenServicio(JwtEncoder encoder, @Value("${tienda.token.duracion}") Duration duracion) {
        this.encoder = encoder;
        this.duracion = duracion;
    }

    /**
     * Issues a signed JWT for the user, valid for {@code tienda.token.duracion} (2 hours by default).
     * @param usuario an authenticated account
     * @return the token plus the data the client shows (name, e-mail, role, expiration)
     */
    public RespuestaSesion emitir(Usuario usuario) {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expira = ahora.plus(duracion);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(usuario.getEmail())
                .issuedAt(ahora)
                .expiresAt(expira)
                .claim("nombre", usuario.getNombre())
                .claim("roles", List.of(usuario.getRol().name()))
                .build();
        JwsHeader cabecera = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new RespuestaSesion(token, "Bearer", expira, usuario.getNombre(), usuario.getEmail(), usuario.getRol().name());
    }
}
