package com.tienda.comandos.api;

import com.tienda.comandos.aplicacion.CalzadoServicio;
import com.tienda.comandos.aplicacion.PedidoServicio;
import com.tienda.comandos.aplicacion.UsuarioActual;
import com.tienda.comandos.config.SeguridadConfig;
import com.tienda.comandos.dominio.AccesoDenegadoException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checks the permission matrix of SeguridadConfig with fake (already "verified") tokens.
 * Only the web layer is started: services are mocks, no database or Kafka needed.
 */
@WebMvcTest(controllers = {CalzadoComandoController.class, PedidoComandoController.class})
@Import(SeguridadConfig.class)
class SeguridadComandosTest {

    private static final String CALZADO = """
            {"marca":"Nike","modelo":"Pegasus","categoria":"deportivo","talla":41,
             "color":"negro","precio":529900,"stockInicial":3}""";

    @Autowired
    private MockMvc mvc;
    @MockBean
    private CalzadoServicio calzadoServicio;
    @MockBean
    private PedidoServicio pedidoServicio;
    @MockBean
    private JwtDecoder jwtDecoder;   // not used: jwt() below skips the real signature check

    private static RequestPostProcessor usuario(String email, String nombre, String rol) {
        return jwt().jwt(t -> t.subject(email).claim("nombre", nombre).claim("roles", List.of(rol)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private static RequestPostProcessor cliente() {
        return usuario("ana@correo.com", "Ana Rojas", "CLIENTE");
    }

    private static RequestPostProcessor admin() {
        return usuario("admin@tienda.com", "Administrador", "ADMIN");
    }

    @Test
    void sinTokenNoSePuedeComprar() throws Exception {
        mvc.perform(post("/api/pedidos").contentType(MediaType.APPLICATION_JSON).content("{\"calzadoId\":1,\"cantidad\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unClientePuedeComprarYElCompradorSaleDelTokenNoDelCuerpo() throws Exception {
        when(pedidoServicio.crear(any(), any())).thenReturn(9L);

        // The attacker adds someone else's e-mail to the body: it must be ignored
        mvc.perform(post("/api/pedidos").with(cliente()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"calzadoId\":1,\"cantidad\":1,\"clienteEmail\":\"victima@correo.com\"}"))
                .andExpect(status().isCreated());

        verify(pedidoServicio).crear(any(), eq(new UsuarioActual("ana@correo.com", "Ana Rojas", false)));
    }

    @Test
    void unClienteNoPuedeTocarElInventario() throws Exception {
        mvc.perform(post("/api/calzados").with(cliente()).contentType(MediaType.APPLICATION_JSON).content(CALZADO))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/calzados/1/reponer-stock").with(cliente()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":5}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(calzadoServicio);
    }

    @Test
    void unAdminPuedeRegistrarCalzado() throws Exception {
        when(calzadoServicio.registrar(any())).thenReturn(10L);

        mvc.perform(post("/api/calzados").with(admin()).contentType(MediaType.APPLICATION_JSON).content(CALZADO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void soloElAdminDespacha() throws Exception {
        mvc.perform(post("/api/pedidos/5/despachar").with(cliente())).andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/5/despachar").with(admin())).andExpect(status().isOk());
    }

    @Test
    void cancelarElPedidoDeOtroClienteDa403() throws Exception {
        doThrow(new AccesoDenegadoException("Solo puedes cancelar tus propios pedidos"))
                .when(pedidoServicio).cancelar(eq(5L), any());

        mvc.perform(post("/api/pedidos/5/cancelar").with(cliente()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo puedes cancelar tus propios pedidos"));
    }

    @Test
    void rutasNoDeclaradasEstanProhibidas() throws Exception {
        mvc.perform(post("/api/cualquier-cosa").with(admin())).andExpect(status().isForbidden());
    }
}
