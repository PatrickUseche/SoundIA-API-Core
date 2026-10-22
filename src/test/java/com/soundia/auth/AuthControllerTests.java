package com.soundia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AuthControllerTests {

    private static final String SECRET = "una-clave-de-prueba-de-al-menos-32-caracteres!";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static AuthProperties propiedades(boolean enabled) {
        return new AuthProperties(enabled, "dj", "mi-clave-secreta", SECRET,
                Duration.ofHours(8), Duration.ofSeconds(30), 3, Duration.ofMinutes(1));
    }

    @SuppressWarnings("unchecked")
    private static MockMvc montar(AuthProperties propiedades, AuthService servicio) {
        ObjectProvider<AuthService> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(servicio);
        return MockMvcBuilders.standaloneSetup(new AuthController(propiedades, proveedor)).build();
    }

    private static String credenciales(String usuario, String contrasena) {
        return "{\"username\":\"" + usuario + "\",\"password\":\"" + contrasena + "\"}";
    }

    @Nested
    class ConLaAutenticacionEncendida {
        private AuthService servicio;
        private MockMvc mockMvc;

        @BeforeEach
        void crear() {
            AuthProperties propiedades = propiedades(true);
            servicio = new AuthService(propiedades, new MutableClock());
            mockMvc = montar(propiedades, servicio);
        }

        private org.springframework.test.web.servlet.ResultActions iniciarSesion(String usuario, String contrasena)
                throws Exception {
            return mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON).content(credenciales(usuario, contrasena)));
        }

        @Test
        void elEstadoDiceQueEstaEncendida() throws Exception {
            mockMvc.perform(get("/api/v1/auth/status"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        }

        @Test
        void conLasCredencialesCorrectasDevuelveUnTokenValidoYCuandoCaduca() throws Exception {
            String cuerpo = iniciarSesion("dj", "mi-clave-secreta")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.expiresAt").exists())
                    .andReturn().getResponse().getContentAsString();

            JsonNode json = JSON.readTree(cuerpo);
            assertThat(servicio.tokens().validate(json.get("token").asString(), TokenService.SCOPE_ACCESS)).contains("dj");
        }

        @Test
        void conCredencialesIncorrectasDevuelve401ConUnMensajeGenerico() throws Exception {
            String contrasenaMal = iniciarSesion("dj", "mala")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Usuario o contrasena incorrectos"))
                    .andReturn().getResponse().getContentAsString();
            String usuarioMal = iniciarSesion("intruso", "mi-clave-secreta")
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            assertThat(usuarioMal).isEqualTo(contrasenaMal);
            assertThat(contrasenaMal).doesNotContain("mala").doesNotContain("mi-clave-secreta");
        }

        @Test
        void trasVariosIntentosFallidosDevuelve429ConRetryAfter() throws Exception {
            for (int i = 0; i < 3; i++) {
                iniciarSesion("dj", "mala").andExpect(status().isUnauthorized());
            }

            iniciarSesion("dj", "mi-clave-secreta")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"));
        }

        @Test
        void conCamposVaciosOSinCuerpoDevuelve400() throws Exception {
            iniciarSesion("", "").andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("no-json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void rechazaCredencialesDemasiadoLargas() throws Exception {
            iniciarSesion("dj", "x".repeat(201)).andExpect(status().isBadRequest());
        }

        @Test
        void elTicketDelStreamEsUnTokenDeVidaCortaSoloParaElStream() throws Exception {
            String cuerpo = mockMvc.perform(post("/api/v1/auth/stream-ticket"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode json = JSON.readTree(cuerpo);
            String ticket = json.get("ticket").asString();
            assertThat(servicio.tokens().validate(ticket, TokenService.SCOPE_STREAM)).contains("dj");
            assertThat(servicio.tokens().validate(ticket, TokenService.SCOPE_ACCESS)).isEmpty();
        }
    }

    @Nested
    class ConLaAutenticacionApagada {
        private final MockMvc mockMvc = montar(propiedades(false), null);

        @Test
        void elEstadoDiceQueEstaApagada() throws Exception {
            mockMvc.perform(get("/api/v1/auth/status"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        }

        @Test
        void elLoginYElTicketNoExisten() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON).content(credenciales("dj", "mi-clave-secreta")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/api/v1/auth/stream-ticket")).andExpect(status().isNotFound());
        }
    }
}
