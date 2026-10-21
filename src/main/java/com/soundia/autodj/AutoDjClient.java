package com.soundia.autodj;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.soundia.config.IaEngineProperties;

/**
 * Pasarela hacia el motor de mezcla de Django: que pista sigue y como hacer la transicion.
 * Devuelve el JSON del motor sin tocarlo.
 */
@Component
public class AutoDjClient {

    private final RestTemplate restTemplate;
    private final IaEngineProperties properties;

    public AutoDjClient(RestTemplate restTemplate, IaEngineProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /** Las mejores pistas para seguir a `trackId`; `limit` puede ser null (el motor usa su valor por defecto). */
    public String nextTracks(long trackId, Integer limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trackId", trackId);
        if (limit != null) {
            body.put("limit", limit);
        }
        return post("next", body);
    }

    /** Plan para pasar de una pista a otra: estilo, compases, puntos de mezcla y curvas de EQ. */
    public String transition(long fromTrackId, long toTrackId) {
        return post("transition", Map.of("fromTrackId", fromTrackId, "toTrackId", toTrackId));
    }

    private String post(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForObject(properties.mixUrl(path), new HttpEntity<>(body, headers), String.class);
    }
}
