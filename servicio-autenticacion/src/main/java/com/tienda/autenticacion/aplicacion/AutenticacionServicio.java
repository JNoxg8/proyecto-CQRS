package com.tienda.autenticacion.aplicacion;

import com.tienda.autenticacion.api.LoginSolicitud;
import com.tienda.autenticacion.api.RegistroSolicitud;
import com.tienda.autenticacion.api.RespuestaSesion;
import com.tienda.autenticacion.dominio.CorreoYaRegistradoException;
import com.tienda.autenticacion.dominio.CredencialesInvalidasException;
import com.tienda.autenticacion.dominio.Rol;
import com.tienda.autenticacion.dominio.Usuario;
import com.tienda.autenticacion.dominio.UsuarioRepository;
import com.tienda.autenticacion.token.TokenServicio;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Use cases of the identity provider: register a customer and verify credentials.
 * Passwords are only compared through the {@link PasswordEncoder} (BCrypt), never in plain text.
 */
@Service
public class AutenticacionServicio {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final TokenServicio tokens;
    /** Hash used when the e-mail does not exist, so a failed login always takes the same time (see iniciarSesion). */
    private final String hashFicticio;

    public AutenticacionServicio(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, TokenServicio tokens) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.hashFicticio = passwordEncoder.encode("contrasena-ficticia-para-igualar-tiempos");
    }

    /** Public sign-up: always creates a CLIENTE. Nobody can register themselves as ADMIN. */
    @Transactional
    public RespuestaSesion registrar(RegistroSolicitud solicitud) {
        String email = normalizar(solicitud.email());
        if (usuarios.existsByEmail(email)) {
            throw new CorreoYaRegistradoException(email);
        }
        Usuario nuevo = usuarios.save(new Usuario(solicitud.nombre().trim(), email,
                passwordEncoder.encode(solicitud.password()), Rol.CLIENTE));
        return tokens.emitir(nuevo);   // signing up also logs you in
    }

    /**
     * Verifies the credentials and issues a token.
     * @throws CredencialesInvalidasException if the e-mail does not exist or the password does not match
     */
    @Transactional(readOnly = true)
    public RespuestaSesion iniciarSesion(LoginSolicitud solicitud) {
        Optional<Usuario> usuario = usuarios.findByEmail(normalizar(solicitud.email()));
        // BCrypt is slow on purpose (~100 ms). If we only ran it for existing e-mails, an attacker could
        // measure the response time to learn which e-mails are registered. So we always run it once.
        String hash = usuario.map(Usuario::getPasswordHash).orElse(hashFicticio);
        boolean coincide = passwordEncoder.matches(solicitud.password(), hash);
        if (usuario.isEmpty() || !coincide) {
            throw new CredencialesInvalidasException();
        }
        return tokens.emitir(usuario.get());
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase();
    }
}
