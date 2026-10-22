package com.soundia.track;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Spring completo (con H2) hablando con un "Django" simulado en un puerto local. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrackProxyIntegrationTests {

    private static HttpServer django;
    private static final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private static volatile int status = 201;
    private static volatile String respuesta = "{\"id\":1,\"title\":\"tema.wav\",\"bpm\":127.98,\"camelot\":\"8A\"}";

    @BeforeAll
    static void iniciarDjangoSimulado() throws IOException {
        django = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        django.createContext("/api/audio/", TrackProxyIntegrationTests::responder);
        django.start();
    }

    @AfterAll
    static void detenerDjangoSimulado() {
        django.stop(0);
    }

    @DynamicPropertySource
    static void apuntarAlDjangoSimulado(DynamicPropertyRegistry registry) throws IOException {
        // El servidor debe existir antes de que Spring lea la propiedad.
        if (django == null) {
            iniciarDjangoSimulado();
        }
        registry.add("soundia.ia-engine.base-url", () -> "http://localhost:" + django.getAddress().getPort());
    }

    private static final byte[] AUDIO = {'R', 'I', 'F', 'F', 10, 20, 30, 40, 50};

    private static void responder(HttpExchange exchange) throws IOException {
        cuerpoRecibido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
        if (exchange.getRequestURI().getPath().endsWith("/file") && status == 200) {
            exchange.getResponseHeaders().add("Content-Type", "audio/wav");
            exchange.sendResponseHeaders(200, AUDIO.length);
            exchange.getResponseBody().write(AUDIO);
            exchange.close();
            return;
        }
        byte[] body = respuesta.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void elAudioSubidoLlegaADjangoYLaRespuestaVuelveAlCliente() throws Exception {
        status = 201;
        respuesta = "{\"id\":1,\"title\":\"tema.wav\",\"bpm\":127.98,\"camelot\":\"8A\"}";

        mockMvc.perform(multipart("/api/v1/tracks/analyze")
                        .file(new MockMultipartFile("file", "tema.wav", "audio/wav", "RIFFcontenido".getBytes()))
                        .param("title", "Mi tema"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.camelot").value("8A"))
                .andExpect(jsonPath("$.bpm").value(127.98));

        String recibido = cuerpoRecibido.get();
        assertTrue(recibido.contains("filename=\"tema.wav\""), recibido);
        assertTrue(recibido.contains("RIFFcontenido"), recibido);
        assertTrue(recibido.contains("Mi tema"), recibido);
    }

    @Test
    void unaPistaQueDjangoNoPuedeAnalizarLlegaAlClienteConSuMensaje() throws Exception {
        status = 422;
        respuesta = "{\"error\":\"El audio dura menos de 2.0 segundos.\"}";

        mockMvc.perform(multipart("/api/v1/tracks/analyze")
                        .file(new MockMultipartFile("file", "corto.wav", "audio/wav", new byte[] {1, 2, 3})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("El audio dura menos de 2.0 segundos."));
    }

    @Test
    void laListaDePistasSeConsultaEnDjango() throws Exception {
        status = 200;
        respuesta = "{\"tracks\":[{\"id\":3,\"title\":\"otra\",\"camelot\":\"5A\"}]}";

        mockMvc.perform(get("/api/v1/tracks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracks[0].camelot").value("5A"));
    }

    @Test
    void unaPistaInexistenteEnDjangoDa404() throws Exception {
        status = 404;
        respuesta = "{\"error\":\"Pista no encontrada\"}";

        mockMvc.perform(get("/api/v1/tracks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Pista no encontrada"));
    }

    @Test
    void siDjangoFallaConUn500ElClienteRecibeUn502Generico() throws Exception {
        status = 500;
        respuesta = "{\"error\":\"traceback interno con rutas\"}";

        mockMvc.perform(get("/api/v1/tracks"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("El motor de IA no esta disponible"));
    }

    @Test
    void elAudioDeUnaPistaViajaDeDjangoAlClienteSinAlterarse() throws Exception {
        status = 200;

        byte[] recibido = mockMvc.perform(get("/api/v1/tracks/5/audio"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType("audio/wav"))
                .andReturn().getResponse().getContentAsByteArray();

        org.junit.jupiter.api.Assertions.assertArrayEquals(AUDIO, recibido);
    }

    @Test
    void unaPistaSinAudioEnDjangoLlegaComo404ConSuMensaje() throws Exception {
        status = 404;
        respuesta = "{\"error\":\"La pista no tiene audio guardado.\"}";

        mockMvc.perform(get("/api/v1/tracks/5/audio"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("La pista no tiene audio guardado."));
    }
}
