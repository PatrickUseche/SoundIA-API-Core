package com.soundia.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.web.client.RestClientException;

import com.soundia.config.IaEngineProperties;
import com.soundia.config.RestClientConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Prueba el cliente contra un servidor HTTP local que hace de Django. */
class TrackClientTests {

    private static final String PISTA = "{\"id\":1,\"title\":\"tema.wav\",\"bpm\":128.0,\"camelot\":\"8B\"}";

    private HttpServer server;
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<String> contentLength = new AtomicReference<>();
    private final AtomicReference<String> transferEncoding = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String respuesta = PISTA;
    private volatile byte[] audioDeRespuesta = null;
    private volatile String tipoDeAudio = "application/json";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/audio/", exchange -> {
            metodo.set(exchange.getRequestMethod());
            ruta.set(exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            cuerpo.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            responder(exchange);
        });
        server.start();
    }

    private void responder(HttpExchange exchange) throws IOException {
        byte[] body = audioDeRespuesta != null ? audioDeRespuesta : respuesta.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", audioDeRespuesta != null ? tipoDeAudio : "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private TrackClient client() {
        IaEngineProperties props = new IaEngineProperties(
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
        RestClientConfig config = new RestClientConfig();
        return new TrackClient(config.iaEngineAnalysisRestTemplate(props), config.iaEngineRestTemplate(props), props);
    }

    @Test
    void analizaEnviandoElAudioComoMultipartYDevuelveElJsonSinTocar() {
        status = 201;

        String resultado = client().analyze("tema.wav", "Mi tema", "RIFFdatos".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(PISTA, resultado);
        assertEquals("POST", metodo.get());
        assertEquals("/api/audio/analyze", ruta.get());
        assertTrue(contentType.get().startsWith("multipart/form-data"), contentType.get());
        assertTrue(contentType.get().contains("boundary="), contentType.get());
    }

    @Test
    void laSubidaLlevaContentLengthYNoVaEnChunks() {
        client().analyze("tema.wav", "Mi tema", "RIFFdatos".getBytes(StandardCharsets.ISO_8859_1));

        // Django con runserver (WSGI) no entiende Transfer-Encoding: chunked.
        assertNull(transferEncoding.get(), transferEncoding.get());
        assertEquals(String.valueOf(cuerpo.get().length()), contentLength.get());
    }

    @Test
    void elMultipartIncluyeElArchivoConSuNombreYElTitulo() {
        client().analyze("tema.wav", "Mi tema", "RIFFdatos".getBytes(StandardCharsets.ISO_8859_1));

        String body = cuerpo.get();
        assertTrue(body.contains("name=\"file\"; filename=\"tema.wav\""), body);
        assertTrue(body.contains("RIFFdatos"), body);
        assertTrue(body.contains("name=\"title\""), body);
        assertTrue(body.contains("Mi tema"), body);
    }

    @Test
    void sinTituloNoEnviaElCampo() {
        client().analyze("tema.wav", null, new byte[] {1, 2, 3});
        assertFalse(cuerpo.get().contains("name=\"title\""), cuerpo.get());

        client().analyze("tema.wav", "   ", new byte[] {1, 2, 3});
        assertFalse(cuerpo.get().contains("name=\"title\""), cuerpo.get());
    }

    @Test
    void listaLasPistas() {
        respuesta = "{\"tracks\":[" + PISTA + "]}";

        String resultado = client().listTracks();

        assertEquals(respuesta, resultado);
        assertEquals("GET", metodo.get());
        assertEquals("/api/audio/tracks", ruta.get());
    }

    @Test
    void obtieneUnaPistaPorId() {
        String resultado = client().getTrack(7);

        assertEquals(PISTA, resultado);
        assertEquals("/api/audio/tracks/7", ruta.get());
    }

    @Test
    void unErrorDelCliente_seConservaConSuCodigoYCuerpo() {
        status = 422;
        respuesta = "{\"error\":\"El audio es silencio; no hay nada que analizar.\"}";

        HttpClientErrorException error = assertThrows(HttpClientErrorException.class,
                () -> client().analyze("tema.wav", null, new byte[] {1}));

        assertEquals(422, error.getStatusCode().value());
        assertTrue(error.getResponseBodyAsString().contains("silencio"));
    }

    @Test
    void unFalloDelMotorSePropagaComoErrorDeCliente() {
        status = 500;
        respuesta = "{\"error\":\"boom\"}";

        assertThrows(RestClientException.class, () -> client().listTracks());
    }

    @Test
    void descargaElAudioDeUnaPistaConSuTipoDeContenido() {
        byte[] audio = {'R', 'I', 'F', 'F', 1, 2, 3};
        audioDeRespuesta = audio;
        tipoDeAudio = "audio/wav";

        var respuesta = client().getTrackAudio(7);

        assertEquals(200, respuesta.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertArrayEquals(audio, respuesta.getBody());
        assertEquals("audio/wav", respuesta.getHeaders().getContentType().toString());
        assertEquals("/api/audio/tracks/7/file", ruta.get());
    }

    @Test
    void siLaPistaNoTieneAudioSePropagaElError404() {
        status = 404;
        respuesta = "{\"error\":\"La pista no tiene audio guardado.\"}";

        HttpClientErrorException error = assertThrows(HttpClientErrorException.class, () -> client().getTrackAudio(7));

        assertEquals(404, error.getStatusCode().value());
    }
}
