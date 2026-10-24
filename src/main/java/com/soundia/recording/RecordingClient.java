package com.soundia.recording;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.soundia.config.IaEngineProperties;

/**
 * Pasarela hacia las grabaciones de mezclas del motor de IA (Django es el dueno de los datos). Reenvia el JSON tal
 * cual: la validacion de verdad (pistas, segundos, curvas) la hace el motor, que tiene los datos de las pistas.
 */
@Component
public class RecordingClient {

    private final RestTemplate restTemplate;
    private final IaEngineProperties properties;

    public RecordingClient(RestTemplate restTemplate, IaEngineProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /** Guarda una transicion tal como la hizo el DJ; devuelve la grabacion con sus rasgos. */
    public String create(String json) {
        return send(HttpMethod.POST, URI.create(properties.mixUrl("recordings")), json);
    }

    /** Las grabaciones mas recientes (sin curvas); `quality` puede ser null para no filtrar. */
    public String list(String quality) {
        // Se pasa un URI ya codificado: con un String, RestTemplate codificaria el valor por segunda vez.
        URI uri = UriComponentsBuilder.fromUriString(properties.mixUrl("recordings"))
                .queryParamIfPresent("quality", Optional.ofNullable(quality).filter(q -> !q.isBlank()))
                .build().encode().toUri();
        return send(HttpMethod.GET, uri, null);
    }

    public String get(long id) {
        return send(HttpMethod.GET, URI.create(properties.mixUrl("recordings/" + id)), null);
    }

    /** La revision del DJ: calidad (`good`, `discard`, `unreviewed`) y notas. */
    public String review(long id, String json) {
        return send(HttpMethod.PUT, URI.create(properties.mixUrl("recordings/" + id)), json);
    }

    /** Copia de seguridad: todas las grabaciones en un JSON (sin ids de pista). */
    public String exportAll() {
        return send(HttpMethod.GET, URI.create(properties.mixUrl("recordings/export")), null);
    }

    /** Restaura una copia exportada; devuelve `{created, skipped}`. Todo o nada. */
    public String importAll(String json) {
        return send(HttpMethod.POST, URI.create(properties.mixUrl("recordings/import")), json);
    }

    public void delete(long id) {
        send(HttpMethod.DELETE, URI.create(properties.mixUrl("recordings/" + id)), null);
    }

    private String send(HttpMethod method, URI url, String json) {
        HttpHeaders headers = new HttpHeaders();
        if (json != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(url, method, new HttpEntity<>(json, headers), String.class).getBody();
    }
}
