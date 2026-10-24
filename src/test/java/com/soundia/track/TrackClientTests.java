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
import java.util.concurrent.atomic.AtomicInteger;
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
    private final AtomicInteger peticionesPost = new AtomicInteger();
    /** Cuantos POST seguidos responde con este codigo antes de responder con normalidad. */
    private volatile int fallosPrevios = 0;
    private volatile int statusDeFallo = 502;
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
            if ("POST".equals(exchange.getRequestMethod()) && peticionesPost.incrementAndGet() <= fallosPrevios) {
                exchange.sendResponseHeaders(statusDeFallo, -1);
                exchange.close();
                return;
            }
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
        TrackClient client = new TrackClient(config.iaEngineAnalysisRestTemplate(props), config.iaEngineRestTemplate(props), props);
        client.setRetryDelay(Duration.ofMillis(10));
        return client;
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
    void antesDeSubirElAudioDespiertaAlMotorConUnaConsultaLigera() {
        client().analyze("tema.wav", "Mi tema", "RIFFdatos".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(1, peticionesPost.get());
        assertEquals("POST", metodo.get());
        assertEquals("/api/audio/analyze", ruta.get());
    }

    @Test
    void siElMotorDespiertaDevolviendo502ReintentaYAlFinalAnaliza() {
        fallosPrevios = 2;

        String resultado = client().analyze("tema.wav", "Mi tema", "RIFFdatos".getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(PISTA, resultado);
        assertEquals(3, peticionesPost.get());
    }

    @Test
    void conUn503TambienReintenta() {
        fallosPrevios = 1;
        statusDeFallo = 503;

        assertEquals(PISTA, client().analyze("tema.wav", null, new byte[] {1}));
        assertEquals(2, peticionesPost.get());
    }

    @Test
    void sinoSeRecuperaDejaDeReintentarAlTercerIntentoYPropagaElError() {
        fallosPrevios = 10;

        assertThrows(org.springframework.web.client.HttpServerErrorException.class,
                () -> client().analyze("tema.wav", null, new byte[] {1}));

        assertEquals(TrackClient.MAX_ANALYZE_ATTEMPTS, peticionesPost.get());
    }

    @Test
    void unErrorDelAnalisisNoSeReintenta() {
        fallosPrevios = 10;
        statusDeFallo = 422;

        assertThrows(HttpClientErrorException.class, () -> client().analyze("silencio.wav", null, new byte[] {1}));

        assertEquals(1, peticionesPost.get());
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

    @Test
    void fijaElPrimerTiempoConUnPutYDevuelveLaPistaSinTocar() {
        String resultado = client().setDownbeat(7, 4.6);

        assertEquals(PISTA, resultado);
        assertEquals("PUT", metodo.get());
        assertEquals("/api/audio/tracks/7/grid", ruta.get());
        assertTrue(contentType.get().startsWith("application/json"), contentType.get());
        assertEquals("{\"downbeat\":4.6}", cuerpo.get());
    }

    @Test
    void borraLaPistaConUnDelete() {
        status = 204;
        respuesta = "";

        client().deleteTrack(7);

        assertEquals("DELETE", metodo.get());
        assertEquals("/api/audio/tracks/7", ruta.get());
    }

    @Test
    void siDjangoNoEncuentraLaPistaAlBorrarPropagaElError() {
        status = 404;
        respuesta = "{\"error\":\"Pista no encontrada\"}";

        HttpClientErrorException e = assertThrows(HttpClientErrorException.class, () -> client().deleteTrack(99));

        assertEquals(404, e.getStatusCode().value());
    }

    @Test
    void siDjangoNoPuedeBorrarElAudioPropagaElErrorDelServidor() {
        status = 502;
        respuesta = "{\"error\":\"No se pudo borrar el audio\"}";

        assertThrows(org.springframework.web.client.HttpServerErrorException.class, () -> client().deleteTrack(7));
    }

    @Test
    void siDjangoRechazaElPrimerTiempoPropagaElError() {
        status = 409;
        respuesta = "{\"error\":\"La pista no tiene una rejilla de pulsos\"}";

        HttpClientErrorException e = assertThrows(HttpClientErrorException.class, () -> client().setDownbeat(7, 4.6));

        assertEquals(409, e.getStatusCode().value());
    }

}
