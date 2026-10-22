package com.soundia.telemetry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.web.client.RestClientException;

import com.soundia.config.IaEngineProperties;
import com.soundia.config.RestClientConfig;
import com.soundia.telemetry.dto.AiDecisionDto;
import com.sun.net.httpserver.HttpServer;

/** Prueba el cliente contra un servidor HTTP local que hace de Django. */
class IaEngineClientTests {

    private HttpServer server;
    private final AtomicReference<String> bodyRecibido = new AtomicReference<>();
    private final AtomicReference<String> contentTypeRecibido = new AtomicReference<>();
    private volatile int statusDeRespuesta = 200;
    private volatile String respuesta = "{\"status\":\"success\",\"action\":\"Corte de EQ en deck 14\"}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/mix/calculate", exchange -> {
            bodyRecibido.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentTypeRecibido.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            byte[] body = respuesta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusDeRespuesta, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private IaEngineClient client() {
        IaEngineProperties props = new IaEngineProperties(
                "http://localhost:" + server.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(2),
                Duration.ofSeconds(60));
        return new IaEngineClient(new RestClientConfig().iaEngineRestTemplate(props), props);
    }

    @Test
    void devuelveLaDecisionCalculadaPorElMotor() {
        AiDecisionDto decision = client().calculateMix(14, 127);

        assertEquals("success", decision.status());
        assertEquals("Corte de EQ en deck 14", decision.action());
    }

    @Test
    void enviaElEventoComoJsonConControlIdYValue() {
        client().calculateMix(14, 127);

        assertTrue(bodyRecibido.get().contains("\"controlId\":14"), bodyRecibido.get());
        assertTrue(bodyRecibido.get().contains("\"value\":127"), bodyRecibido.get());
        assertTrue(contentTypeRecibido.get().startsWith("application/json"), contentTypeRecibido.get());
    }

    @Test
    void propagaUnErrorSiElMotorRespondeConFallo() {
        statusDeRespuesta = 500;
        respuesta = "{\"error\":\"boom\"}";

        assertThrows(RestClientException.class, () -> client().calculateMix(14, 127));
    }
}
