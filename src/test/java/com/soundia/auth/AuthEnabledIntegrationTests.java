package com.soundia.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

/** La autenticacion encendida con el contexto real: el filtro protege el API de verdad. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "soundia.auth.enabled=true",
        "soundia.auth.username=dj",
        "soundia.auth.password=mi-clave-secreta",
        "soundia.auth.jwt-secret=una-clave-de-prueba-de-al-menos-32-caracteres!",
        "soundia.cors.allowed-origins=http://localhost:4200"
})
class AuthEnabledIntegrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    private String iniciarSesion() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dj\",\"password\":\"mi-clave-secreta\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(cuerpo).get("token").asString();
    }

    @Test
    void elEstadoEsPublicoYDiceQueHayQuePedirLogin() throws Exception {
        mockMvc.perform(get("/api/v1/auth/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void unaRutaDelApiSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/midi/logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void conElTokenDelLoginLaRutaRespondeNormal() throws Exception {
        String token = iniciarSesion();

        mockMvc.perform(get("/api/v1/midi/logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void conUnaContrasenaIncorrectaNoSeObtieneToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dj\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elTicketDelStreamSeObtieneConElTokenYSoloAbreElStream() throws Exception {
        String token = iniciarSesion();
        String cuerpo = mockMvc.perform(post("/api/v1/auth/stream-ticket")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticket = JSON.readTree(cuerpo).get("ticket").asString();

        // Sin token no se puede pedir un ticket, y el ticket no sirve para el resto de rutas.
        mockMvc.perform(post("/api/v1/auth/stream-ticket")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/midi/logs").param("ticket", ticket)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/midi/stream").param("ticket", ticket))
                .andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    @Test
    void elPreflightDeCorsDelFrontendPasaSinTokenYLaRespuesta401TambienLlevaCors() throws Exception {
        mockMvc.perform(options("/api/v1/midi/logs")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

        // Sin las cabeceras de CORS en el 401, el navegador mostraria un error de red en vez de "no autorizado".
        mockMvc.perform(get("/api/v1/midi/logs").header("Origin", "http://localhost:4200"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void unOrigenNoPermitidoSigueRechazado() throws Exception {
        mockMvc.perform(options("/api/v1/midi/logs")
                        .header("Origin", "http://sitio-malicioso.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void laRespuestaDeLoginNoRevelaLaContrasenaNiSeGuardaEnClaro() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dj\",\"password\":\"mala\"}"))
                .andExpect(jsonPath("$.error").value("Usuario o contrasena incorrectos"))
                .andExpect(jsonPath("$.detalles", org.hamcrest.Matchers.not(hasItem("mi-clave-secreta"))));
    }
}
