package com.tienda.autenticacion.config;

import com.tienda.autenticacion.dominio.Rol;
import com.tienda.autenticacion.dominio.Usuario;
import com.tienda.autenticacion.dominio.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the demo accounts if they do not exist. This is the ONLY way to get an ADMIN:
 * the public sign-up always creates CLIENTE accounts.
 */
@Component
public class CargaUsuariosIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaUsuariosIniciales.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final UsuariosIniciales iniciales;

    public CargaUsuariosIniciales(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, UsuariosIniciales iniciales) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.iniciales = iniciales;
    }

    /** Runs once at startup, after the Spring context is ready. */
    @Override
    public void run(String... args) {
        crearSiNoExiste(iniciales.admin(), Rol.ADMIN);
        crearSiNoExiste(iniciales.cliente(), Rol.CLIENTE);
    }

    private void crearSiNoExiste(UsuariosIniciales.Cuenta cuenta, Rol rol) {
        if (cuenta == null || usuarios.existsByEmail(cuenta.email())) {
            return;
        }
        usuarios.save(new Usuario(cuenta.nombre(), cuenta.email(), passwordEncoder.encode(cuenta.password()), rol));
        log.info("Cuenta de demostración creada: {} ({})", cuenta.email(), rol);
    }
}
