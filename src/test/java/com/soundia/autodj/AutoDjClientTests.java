package com.soundia.autodj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;

import com.soundia.config.IaEngineProperties;
import com.soundia.config.RestClientConfig;
import com.soundia.engine.EngineStatus;
import com.sun.net.httpserver.HttpServer;

/** Prueba el cliente contra un servidor HTTP local que hace de Django. */
class AutoDjClientTests {

    private HttpServer server;
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String respuesta = "{\"ok\":true}";

    @BeforeEach
    void startServer() throws IOException {
        EngineStatus.getInstance().reset();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/mix/", exchange -> {
            ruta.set(exchange.getRequestURI().getPath());
            metodo.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            cuerpo.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = respuesta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        EngineStatus.getInstance().reset();
    }

    private AutoDjClient client() {
        IaEngineProperties props = new IaEngineProperties(
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
        return new AutoDjClient(new RestClientConfig().iaEngineRestTemplate(props), props);
    }

    @Test
    void pideLasSiguientesPistasAlMotorYDevuelveSuJson() {
        respuesta = "{\"candidates\":[]}";

        String resultado = client().nextTracks(7, 3);

        assertEquals("{\"candidates\":[]}", resultado);
        assertEquals("POST", metodo.get());
        assertEquals("/api/mix/next", ruta.get());
        assertTrue(contentType.get().startsWith("application/json"), contentType.get());
        assertTrue(cuerpo.get().contains("\"trackId\":7"), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"limit\":3"), cuerpo.get());
    }

    @Test
    void sinLimiteNoEnviaElCampo() {
        client().nextTracks(7, null);

        assertTrue(cuerpo.get().contains("\"trackId\":7"), cuerpo.get());
        assertFalse(cuerpo.get().contains("limit"), cuerpo.get());
    }

    @Test
    void pideElPlanDeTransicionConLosDosIds() {
        respuesta = "{\"style\":\"blend\",\"bars\":16}";

        String resultado = client().transition(1, 2);

        assertEquals("{\"style\":\"blend\",\"bars\":16}", resultado);
        assertEquals("/api/mix/transition", ruta.get());
        assertTrue(cuerpo.get().contains("\"fromTrackId\":1"), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"toTrackId\":2"), cuerpo.get());
    }

    @Test
    void unaPistaInexistenteEnElMotorSePropagaComoError404() {
        status = 404;
        respuesta = "{\"error\":\"No existe la pista 9.\"}";

        HttpClientErrorException error = assertThrows(HttpClientErrorException.class, () -> client().nextTracks(9, null));

        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void enviaLaValoracionDeUnaTransicionAlMotor() {
        respuesta = "{\"weights\":{},\"samples\":1,\"learned\":false}";

        String resultado = client().submitFeedback(1, 2, true);

        assertEquals("{\"weights\":{},\"samples\":1,\"learned\":false}", resultado);
        assertEquals("POST", metodo.get());
        assertEquals("/api/mix/feedback", ruta.get());
        assertTrue(cuerpo.get().contains("\"fromTrackId\":1"), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"toTrackId\":2"), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"liked\":true"), cuerpo.get());
    }

    @Test
    void laValoracionNegativaSeEnviaComoFalse() {
        client().submitFeedback(1, 2, false);

        assertTrue(cuerpo.get().contains("\"liked\":false"), cuerpo.get());
    }

    @Test
    void enviaAlMotorCuandoElDjPidioMezclar() {
        respuesta = "{\"samples\":3,\"advanceSeconds\":40.0,\"learned\":true}";

        String resultado = client().submitMixTiming(1, 2, 290.5, 250.0);

        assertEquals("{\"samples\":3,\"advanceSeconds\":40.0,\"learned\":true}", resultado);
        assertEquals("POST", metodo.get());
        assertEquals("/api/mix/timing", ruta.get());
        assertTrue(cuerpo.get().contains("\"fromTrackId\":1"), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"toTrackId\":2"), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"plannedMixOutAt\":290.5"), cuerpo.get());
        assertTrue(cuerpo.get().contains("\"actualMixOutAt\":250.0"), cuerpo.get());
    }

    @Test
    void consultaLosPesosVigentesDelMotor() {
        respuesta = "{\"weights\":{\"tempo\":0.2},\"learned\":true,\"samples\":40}";

        String resultado = client().weights();

        assertEquals("{\"weights\":{\"tempo\":0.2},\"learned\":true,\"samples\":40}", resultado);
        assertEquals("GET", metodo.get());
        assertEquals("/api/mix/weights", ruta.get());
    }

    @Test
    void unaRespuestaCorrectaDejaElMotorArriba() {
        client().weights();

        assertEquals(EngineStatus.State.UP, EngineStatus.getInstance().snapshot().state());
    }

    @Test
    void unErrorDelMotorLoMarcaComoCaido() {
        status = 500;

        assertThrows(org.springframework.web.client.HttpServerErrorException.class, () -> client().nextTracks(7, null));

        assertEquals(EngineStatus.State.DOWN, EngineStatus.getInstance().snapshot().state());
    }

    @Test
    void unErrorDePeticionNoMarcaElMotorComoCaido() {
        status = 404;

        assertThrows(HttpClientErrorException.class, () -> client().transition(1, 2));

        assertEquals(EngineStatus.State.UP, EngineStatus.getInstance().snapshot().state());
    }

    @Test
    void consultaElEstadoDelModeloDeEstilo() {
        respuesta = "{\"goodRecordings\":3,\"required\":20}";

        String resultado = client().styleModel();

        assertEquals("{\"goodRecordings\":3,\"required\":20}", resultado);
        assertEquals("GET", metodo.get());
        assertEquals("/api/mix/style-model", ruta.get());
    }

    @Test
    void enciendeElModeloDeEstiloEnElMotor() {
        client().setStyleModel(true);

        assertEquals("POST", metodo.get());
        assertEquals("/api/mix/style-model", ruta.get());
        assertEquals("{\"enabled\":true}", cuerpo.get());
    }
}
