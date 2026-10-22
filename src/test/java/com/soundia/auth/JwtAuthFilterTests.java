package com.soundia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class JwtAuthFilterTests {

    private static final String SECRET = "una-clave-de-prueba-de-al-menos-32-caracteres!";

    /** Rutas de mentira con las mismas direcciones que protege el filtro. */
    @RestController
    static class RutasDePrueba {
        @GetMapping("/api/v1/algo")
        String algo() {
            return "ok";
        }

        @PostMapping("/api/v1/algo")
        String crear() {
            return "creado";
        }

        @GetMapping("/api/v1/midi/stream")
        String stream() {
            return "stream";
        }

        @PostMapping("/api/v1/midi/stream")
        String streamPost() {
            return "no";
        }

        @PostMapping("/api/v1/auth/login")
        String login() {
            return "login";
        }

        @GetMapping("/api/v1/auth/status")
        String estado() {
            return "estado";
        }

        @GetMapping("/actuator/health")
        String salud() {
            return "up";
        }
    }

    private MutableClock clock;
    private TokenService tokens;
    private MockMvc mockMvc;

    @BeforeEach
    void crear() {
        clock = new MutableClock();
        tokens = new TokenService(SECRET, clock);
        mockMvc = MockMvcBuilders.standaloneSetup(new RutasDePrueba())
                .addFilters(new JwtAuthFilter(tokens)).build();
    }

    private String acceso() {
        return "Bearer " + tokens.issue("dj", TokenService.SCOPE_ACCESS, Duration.ofHours(1)).value();
    }

    private String ticket() {
        return tokens.issue("dj", TokenService.SCOPE_STREAM, Duration.ofSeconds(30)).value();
    }

    @Test
    void sinTokenDevuelve401ConElEsquemaBearerYUnCuerpoJson() throws Exception {
        mockMvc.perform(get("/api/v1/algo"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().json("{\"error\":\"No autorizado\"}"));
    }

    @Test
    void conUnTokenDeAccesoValidoDejaPasar() throws Exception {
        mockMvc.perform(get("/api/v1/algo").header(HttpHeaders.AUTHORIZATION, acceso()))
                .andExpect(status().isOk()).andExpect(content().string("ok"));
        mockMvc.perform(post("/api/v1/algo").header(HttpHeaders.AUTHORIZATION, acceso()))
                .andExpect(status().isOk());
    }

    @Test
    void conUnTokenCaducadoDevuelve401() throws Exception {
        String viejo = acceso();
        clock.advance(Duration.ofHours(2));

        mockMvc.perform(get("/api/v1/algo").header(HttpHeaders.AUTHORIZATION, viejo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conBasuraOEsquemaDistintoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/algo").header(HttpHeaders.AUTHORIZATION, "Bearer basura"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/algo").header(HttpHeaders.AUTHORIZATION, "Basic ZGo6Y2xhdmU="))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/algo").header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTicketDelStreamNoSirveComoTokenDeAcceso() throws Exception {
        mockMvc.perform(get("/api/v1/algo").header(HttpHeaders.AUTHORIZATION, "Bearer " + ticket()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elStreamAceptaUnTicketEnLaUrl() throws Exception {
        mockMvc.perform(get("/api/v1/midi/stream").param("ticket", ticket()))
                .andExpect(status().isOk()).andExpect(content().string("stream"));
    }

    @Test
    void elStreamTambienAceptaElTokenDeAccesoEnLaCabecera() throws Exception {
        mockMvc.perform(get("/api/v1/midi/stream").header(HttpHeaders.AUTHORIZATION, acceso()))
                .andExpect(status().isOk());
    }

    @Test
    void elTokenDeAccesoNoSeAceptaComoTicketEnLaUrl() throws Exception {
        String sinPrefijo = acceso().substring("Bearer ".length());

        mockMvc.perform(get("/api/v1/midi/stream").param("ticket", sinPrefijo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unTicketCaducadoOFalsoNoAbreElStream() throws Exception {
        String viejo = ticket();
        clock.advance(Duration.ofSeconds(31));

        mockMvc.perform(get("/api/v1/midi/stream").param("ticket", viejo)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/midi/stream").param("ticket", "falso")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/midi/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    void elTicketSoloVaEnElStreamNoEnOtrasRutasNiConOtroMetodo() throws Exception {
        mockMvc.perform(get("/api/v1/algo").param("ticket", ticket())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/midi/stream").param("ticket", ticket())).andExpect(status().isUnauthorized());
    }

    @Test
    void siHayCabeceraAuthorizationInvalidaNoSeRecurreAlTicket() throws Exception {
        mockMvc.perform(get("/api/v1/midi/stream").param("ticket", ticket())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer basura"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elLoginYElEstadoSonPublicos() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/status")).andExpect(status().isOk());
    }

    @Test
    void elPreflightDeCorsPasaSinToken() throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("OPTIONS", "/api/v1/algo");
        peticion.addHeader("Origin", "http://localhost:4200");
        peticion.addHeader("Access-Control-Request-Method", "GET");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        MockFilterChain cadena = new MockFilterChain();

        new JwtAuthFilter(tokens).doFilter(peticion, respuesta, cadena);

        assertThat(cadena.getRequest()).isNotNull(); // la peticion siguio su camino
        assertThat(respuesta.getStatus()).isEqualTo(200);
    }

    @Test
    void lasRutasQueNoSonDelApiNoSeFiltran() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
