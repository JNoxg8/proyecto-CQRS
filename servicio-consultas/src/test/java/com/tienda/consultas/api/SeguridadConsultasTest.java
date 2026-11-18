package com.tienda.consultas.api;

import com.tienda.consultas.aplicacion.CatalogoConsultas;
import com.tienda.consultas.config.SeguridadConfig;
import com.tienda.consultas.proyeccion.ProyectorCatalogo;
import com.tienda.consultas.proyeccion.ProyectorPedidos;
import com.tienda.consultas.repositorio.CalzadoVistaRepository;
import com.tienda.consultas.repositorio.PedidoVistaRepository;
import com.tienda.consultas.tiemporeal.NotificadorTiempoReal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Permission matrix of the read side (web layer only, repositories mocked). */
@WebMvcTest(controllers = {CatalogoController.class, PedidoConsultaController.class, ReconstruccionController.class})
@Import(SeguridadConfig.class)
class SeguridadConsultasTest {

    @Autowired
    private MockMvc mvc;
    @MockBean
    private CatalogoConsultas catalogoConsultas;
    @MockBean
    private CalzadoVistaRepository catalogo;
    @MockBean
    private PedidoVistaRepository pedidos;
    @MockBean
    private ProyectorCatalogo proyectorCatalogo;
    @MockBean
    private ProyectorPedidos proyectorPedidos;
    @MockBean
    private NotificadorTiempoReal notificador;
    @MockBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor usuario(String email, String rol) {
        return jwt().jwt(t -> t.subject(email).claim("roles", List.of(rol)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    @Test
    void elCatalogoEsPublico() throws Exception {
        mvc.perform(get("/api/catalogo")).andExpect(status().isOk());
    }

    @Test
    void misPedidosUsaElCorreoDelTokenYNoUnParametro() throws Exception {
        mvc.perform(get("/api/pedidos/mios").param("email", "victima@correo.com").with(usuario("ana@correo.com", "CLIENTE")))
                .andExpect(status().isOk());

        verify(pedidos).findByClienteEmailOrderByCreadoEnDesc("ana@correo.com");
        verify(pedidos, never()).findByClienteEmailOrderByCreadoEnDesc("victima@correo.com");
    }

    @Test
    void misPedidosSinTokenDa401() throws Exception {
        mvc.perform(get("/api/pedidos/mios")).andExpect(status().isUnauthorized());
    }

    @Test
    void soloElAdminVeTodosLosPedidos() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pedidos").with(usuario("ana@correo.com", "CLIENTE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/pedidos").with(usuario("admin@tienda.com", "ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void soloElAdminReconstruyeElModeloDeLectura() throws Exception {
        mvc.perform(post("/api/admin/reconstruir").with(usuario("ana@correo.com", "CLIENTE"))).andExpect(status().isForbidden());
        verifyNoInteractions(catalogo, pedidos);

        mvc.perform(post("/api/admin/reconstruir").with(usuario("admin@tienda.com", "ADMIN"))).andExpect(status().isAccepted());
        verify(catalogo).deleteAll();
    }
}
