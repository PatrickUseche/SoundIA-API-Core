package com.soundia.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Prueba el cliente contra un servidor HTTP local que hace de Django. */
class RecordingClientTests {

    private static final String GRABACION = "{\"id\":3,\"quality\":\"unreviewed\"}";

    private HttpServer server;
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> consulta = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String respuesta = GRABACION;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/mix/", exchange -> {
            metodo.set(exchange.getRequestMethod());
            ruta.set(exchange.getRequestURI().getPath());
            consulta.set(exchange.getRequestURI().getRawQuery());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            cuerpo.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder(exchange);
        });
        server.start();
    }

    private void responder(HttpExchange exchange) throws IOException {
        byte[] body = status == 204 ? new byte[0] : respuesta.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : body.length);
        if (status != 204) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private RecordingClient client() {
        IaEngineProperties props = new IaEngineProperties(
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
        return new RecordingClient(new RestClientConfig().iaEngineRestTemplate(props), props);
    }

    @Test
    void guardaUnaGrabacionConUnPostJsonYDevuelveLaRespuestaSinTocar() {
        status = 201;

        String resultado = client().create("{\"fromTrackId\":1,\"toTrackId\":2}");

        assertEquals(GRABACION, resultado);
        assertEquals("POST", metodo.get());
        assertEquals("/api/mix/recordings", ruta.get());
        assertTrue(contentType.get().startsWith("application/json"), contentType.get());
        assertEquals("{\"fromTrackId\":1,\"toTrackId\":2}", cuerpo.get());
    }

    @Test
    void listaSinFiltro() {
        respuesta = "{\"recordings\":[],\"counts\":{\"total\":0}}";

        assertEquals(respuesta, client().list(null));
        assertEquals("GET", metodo.get());
        assertEquals("/api/mix/recordings", ruta.get());
        assertNull(consulta.get());
        client().list("  ");
        assertNull(consulta.get());
    }

    @Test
    void listaFiltrandoPorCalidadYCodificaElValor() {
        client().list("good");
        assertEquals("quality=good", consulta.get());

        client().list("a&b=c");
        assertEquals("quality=a%26b%3Dc", consulta.get());
    }

    @Test
    void obtieneUnaGrabacion() {
        assertEquals(GRABACION, client().get(3));
        assertEquals("GET", metodo.get());
        assertEquals("/api/mix/recordings/3", ruta.get());
    }

    @Test
    void revisaConUnPut() {
        client().review(3, "{\"quality\":\"good\"}");

        assertEquals("PUT", metodo.get());
        assertEquals("/api/mix/recordings/3", ruta.get());
        assertEquals("{\"quality\":\"good\"}", cuerpo.get());
    }

    @Test
    void borraConUnDelete() {
        status = 204;

        client().delete(3);

        assertEquals("DELETE", metodo.get());
        assertEquals("/api/mix/recordings/3", ruta.get());
    }

    @Test
    void siDjangoRechazaLaGrabacionPropagaElError() {
        status = 400;
        respuesta = "{\"error\":\"overlapSeconds debe ser mayor que 0.\"}";

        HttpClientErrorException e = assertThrows(HttpClientErrorException.class, () -> client().create("{}"));

        assertEquals(400, e.getStatusCode().value());
    }
}
