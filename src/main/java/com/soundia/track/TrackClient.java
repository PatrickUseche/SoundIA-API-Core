package com.soundia.track;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import com.soundia.config.IaEngineProperties;

/**
 * Acceso a las pistas que analiza y guarda el motor de IA (Django es el dueno de los datos de pista).
 * Las respuestas se devuelven como JSON sin tocar: el API Gateway solo hace de pasarela.
 */
@Component
public class TrackClient {

    private static final Logger log = LoggerFactory.getLogger(TrackClient.class);

    /** Intentos de subir una pista cuando el motor esta despertando o se reinicia (servicios que se duermen). */
    static final int MAX_ANALYZE_ATTEMPTS = 3;
    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(502, 503, 504);

    private final RestTemplate analysisRestTemplate;
    private final RestTemplate restTemplate;
    private final IaEngineProperties properties;
    private Duration retryDelay = Duration.ofSeconds(5);

    /** Solo para las pruebas: la espera entre intentos. */
    void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }

    public TrackClient(@Qualifier("iaEngineAnalysisRestTemplate") RestTemplate analysisRestTemplate,
                       RestTemplate restTemplate, IaEngineProperties properties) {
        this.analysisRestTemplate = analysisRestTemplate;
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * Envia el audio a Django para analizarlo; devuelve la pista analizada y guardada (JSON).
     *
     * En la nube gratuita el motor se duerme tras unos minutos sin trafico y tarda cerca de un minuto en despertar:
     * mandarle el audio entero mientras duerme suele acabar en un 502 del proxy. Por eso antes se le hace una consulta
     * ligera (que espera a que despierte) y, si aun asi el proxy responde 502, 503 o 504, o se corta la conexion, se
     * reintenta unas veces. Un error del propio analisis (audio invalido, silencio...) no se reintenta.
     */
    public String analyze(String filename, String title, byte[] content) {
        wakeUp();
        for (int attempt = 1; ; attempt++) {
            try {
                return postAnalyze(filename, title, content);
            } catch (RestClientException e) {
                if (attempt >= MAX_ANALYZE_ATTEMPTS || !isTransient(e)) {
                    throw e;
                }
                log.warn("El motor de IA no respondio al analizar (intento {} de {}): {}. Se reintenta.",
                        attempt, MAX_ANALYZE_ATTEMPTS, e.getMessage());
                pause();
            }
        }
    }

    /** Consulta ligera que despierta al motor si estaba dormido; un fallo aqui no importa, el analisis lo volvera a ver. */
    private void wakeUp() {
        try {
            restTemplate.getForObject(properties.audioUrl("tracks"), String.class);
        } catch (RestClientException e) {
            log.debug("No se pudo despertar al motor de IA antes de analizar: {}", e.getMessage());
        }
    }

    private static boolean isTransient(RestClientException e) {
        if (e instanceof HttpServerErrorException server) {
            return TRANSIENT_STATUSES.contains(server.getStatusCode().value());
        }
        // Conexion rechazada o cortada; un timeout de lectura (el analisis tardo demasiado) no se reintenta.
        return e instanceof ResourceAccessException
                && (e.getCause() instanceof ConnectException || e.getCause() instanceof SocketException);
    }

    private void pause() {
        try {
            Thread.sleep(retryDelay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ResourceAccessException("Interrumpido mientras se esperaba al motor de IA");
        }
    }

    private String postAnalyze(String filename, String title, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new NamedByteArrayResource(content, filename));
        if (title != null && !title.isBlank()) {
            body.add("title", title);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return analysisRestTemplate.postForObject(
                properties.audioUrl("analyze"), new HttpEntity<>(body, headers), String.class);
    }

    public String listTracks() {
        return restTemplate.getForObject(properties.audioUrl("tracks"), String.class);
    }

    public String getTrack(long id) {
        return restTemplate.getForObject(properties.audioUrl("tracks/" + id), String.class);
    }

    /** Cambia el genero de la pista (`{"genre": "salsa"}`); devuelve la pista actualizada (JSON). */
    public String setGenre(long id, String genre) {
        return restTemplate.exchange(properties.audioUrl("tracks/" + id), HttpMethod.PUT,
                new HttpEntity<>(Map.of("genre", genre)), String.class).getBody();
    }

    /** Borra la pista y su audio en el motor de IA. Si no existe, el motor responde 404 y llega al cliente. */
    public void deleteTrack(long id) {
        restTemplate.delete(properties.audioUrl("tracks/" + id));
    }

    /**
     * El DJ senala un instante (segundos) que es un "1" del compas: Django lo ajusta al pulso mas cercano de la
     * rejilla y lo guarda como primer tiempo. Devuelve la pista actualizada (JSON).
     */
    public String setDownbeat(long id, double downbeat) {
        return restTemplate.exchange(properties.audioUrl("tracks/" + id + "/grid"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("downbeat", downbeat)), String.class).getBody();
    }

    /**
     * El audio original de una pista. Se descarga entero (hasta 50 MB) con el RestTemplate de analisis,
     * que tiene un timeout de lectura largo.
     */
    public ResponseEntity<byte[]> getTrackAudio(long id) {
        return analysisRestTemplate.getForEntity(properties.audioUrl("tracks/" + id + "/file"), byte[].class);
    }

    /** Un ByteArrayResource con nombre de archivo, que es lo que hace falta para enviarlo como parte multipart. */
    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] content, String filename) {
            super(content);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
