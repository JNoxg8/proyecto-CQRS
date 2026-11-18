package com.tienda.autenticacion;

import com.nimbusds.jose.jwk.RSAKey;
import com.tienda.autenticacion.api.LoginSolicitud;
import com.tienda.autenticacion.api.RegistroSolicitud;
import com.tienda.autenticacion.api.RespuestaSesion;
import com.tienda.autenticacion.aplicacion.AutenticacionServicio;
import com.tienda.autenticacion.dominio.CorreoYaRegistradoException;
import com.tienda.autenticacion.dominio.CredencialesInvalidasException;
import com.tienda.autenticacion.dominio.Rol;
import com.tienda.autenticacion.dominio.Usuario;
import com.tienda.autenticacion.dominio.UsuarioRepository;
import com.tienda.autenticacion.token.ClavesConfig;
import com.tienda.autenticacion.token.TokenServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Sign-up, login and token signature, with a mocked repository (no database needed). */
class AutenticacionServicioTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);   // low cost: fast tests
    private RSAKey clave;
    private AutenticacionServicio servicio;

    @BeforeEach
    void preparar() throws Exception {
        ClavesConfig claves = new ClavesConfig();
        clave = claves.claveRsa();
        TokenServicio tokens = new TokenServicio(claves.jwtEncoder(clave), Duration.ofHours(2));
        servicio = new AutenticacionServicio(usuarios, encoder, tokens);
        when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Jwt verificarConClavePublica(String token, RSAKey clavePublica) throws Exception {
        return NimbusJwtDecoder.withPublicKey(clavePublica.toRSAPublicKey()).build().decode(token);
    }

    @Test
    void registrarCreaUnClienteYDevuelveUnTokenValido() throws Exception {
        RespuestaSesion sesion = servicio.registrar(new RegistroSolicitud("Luis", " Luis@Correo.com ", "clave-segura-1"));

        Jwt jwt = verificarConClavePublica(sesion.token(), clave);
        assertThat(jwt.getSubject()).isEqualTo("luis@correo.com");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CLIENTE");
        assertThat(jwt.getClaimAsString("nombre")).isEqualTo("Luis");
        verify(usuarios).save(argThat(u -> !u.getPasswordHash().equals("clave-segura-1")));   // never in clear text
    }

    @Test
    void noPermiteDosCuentasConElMismoCorreo() {
        when(usuarios.existsByEmail("ana@correo.com")).thenReturn(true);

        assertThatThrownBy(() -> servicio.registrar(new RegistroSolicitud("Ana", "ana@correo.com", "otra-clave-123")))
                .isInstanceOf(CorreoYaRegistradoException.class);
    }

    @Test
    void loginCorrectoDevuelveElRolDelUsuario() {
        Usuario admin = new Usuario("Admin", "admin@tienda.com", encoder.encode("Admin123*"), Rol.ADMIN);
        when(usuarios.findByEmail("admin@tienda.com")).thenReturn(Optional.of(admin));

        assertThat(servicio.iniciarSesion(new LoginSolicitud("admin@tienda.com", "Admin123*")).rol()).isEqualTo("ADMIN");
    }

    @Test
    void contrasenaIncorrectaYCorreoInexistenteDanElMismoError() {
        Usuario ana = new Usuario("Ana", "ana@correo.com", encoder.encode("Cliente123*"), Rol.CLIENTE);
        when(usuarios.findByEmail("ana@correo.com")).thenReturn(Optional.of(ana));
        when(usuarios.findByEmail("nadie@correo.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciarSesion(new LoginSolicitud("ana@correo.com", "mala")))
                .isInstanceOf(CredencialesInvalidasException.class).hasMessage("Correo o contraseña incorrectos");
        assertThatThrownBy(() -> servicio.iniciarSesion(new LoginSolicitud("nadie@correo.com", "mala")))
                .isInstanceOf(CredencialesInvalidasException.class).hasMessage("Correo o contraseña incorrectos");
    }

    @Test
    void unTokenFirmadoConOtraClaveEsRechazado() throws Exception {
        RespuestaSesion sesion = servicio.registrar(new RegistroSolicitud("Eva", "eva@correo.com", "clave-segura-2"));
        RSAKey otraClave = new ClavesConfig().claveRsa();

        assertThatThrownBy(() -> verificarConClavePublica(sesion.token(), otraClave))
                .isInstanceOf(BadJwtException.class);
    }
}
