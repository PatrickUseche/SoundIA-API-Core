package com.soundia.track;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.soundia.config.IaEngineProperties;

/**
 * Acceso a las pistas que analiza y guarda el motor de IA (Django es el dueno de los datos de pista).
 * Las respuestas se devuelven como JSON sin tocar: el API Gateway solo hace de pasarela.
 */
@Component
public class TrackClient {

    private final RestTemplate analysisRestTemplate;
    private final RestTemplate restTemplate;
    private final IaEngineProperties properties;

    public TrackClient(@Qualifier("iaEngineAnalysisRestTemplate") RestTemplate analysisRestTemplate,
                       RestTemplate restTemplate, IaEngineProperties properties) {
        this.analysisRestTemplate = analysisRestTemplate;
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /** Envia el audio a Django para analizarlo; devuelve la pista analizada y guardada (JSON). */
    public String analyze(String filename, String title, byte[] content) {
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
