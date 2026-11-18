package com.tienda.consultas.api;

import com.tienda.consultas.tiemporeal.NotificadorTiempoReal;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * The browser opens {@code new EventSource("http://localhost:8082/api/stream?access_token=...")}.
 * Without a token it is an anonymous visitor (receives only catalog changes).
 */
@RestController
@RequestMapping("/api/stream")
public class TiempoRealController {

    private final NotificadorTiempoReal notificador;

    public TiempoRealController(NotificadorTiempoReal notificador) {
        this.notificador = notificador;
    }

    /**
     * GET /api/stream. The token is optional (null for visitors).
     * @return an open SSE connection that receives the events this user may see
     */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter suscribir(@AuthenticationPrincipal Jwt token) {
        if (token == null) {
            return notificador.suscribir(null, false);
        }
        List<String> roles = token.getClaimAsStringList("roles");
        return notificador.suscribir(token.getSubject(), roles != null && roles.contains("ADMIN"));
    }
}
