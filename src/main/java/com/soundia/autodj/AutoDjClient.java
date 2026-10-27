package com.soundia.autodj;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.soundia.config.IaEngineProperties;
import com.soundia.engine.EngineStatus;

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
        return post("next", EngineRequestBuilder.create().with("trackId", trackId).withIfPresent("limit", limit).build());
    }

    /** Plan para pasar de una pista a otra: estilo, compases, puntos de mezcla y curvas de EQ. */
    public String transition(long fromTrackId, long toTrackId) {
        return post("transition", EngineRequestBuilder.create().with("fromTrackId", fromTrackId).with("toTrackId", toTrackId).build());
    }

    /** Registra si le gusto al DJ una transicion; el motor reentrena sus pesos con ella. */
    public String submitFeedback(long fromTrackId, long toTrackId, boolean liked) {
        return post("feedback", EngineRequestBuilder.create()
                .with("fromTrackId", fromTrackId).with("toTrackId", toTrackId).with("liked", liked).build());
    }

    /** Registra que el DJ pidio mezclar antes de lo planeado; el motor aprende de ello cuando mezclar. */
    public String submitMixTiming(long fromTrackId, long toTrackId, double plannedMixOutAt, double actualMixOutAt) {
        return post("timing", EngineRequestBuilder.create()
                .with("fromTrackId", fromTrackId).with("toTrackId", toTrackId)
                .with("plannedMixOutAt", plannedMixOutAt).with("actualMixOutAt", actualMixOutAt).build());
    }

    /**
     * Lo que el DJ eligio como siguiente pista tras `fromTrackId` y, si la hay, la que la IA habria propuesto: de la
     * diferencia el motor aprende que pista sigue. `recommendedTrackId` puede ser null.
     */
    public String submitChoice(long fromTrackId, long chosenTrackId, Long recommendedTrackId) {
        return post("choices", EngineRequestBuilder.create()
                .with("fromTrackId", fromTrackId).with("chosenTrackId", chosenTrackId)
                .withIfPresent("recommendedTrackId", recommendedTrackId).build());
    }

    /** Los pesos de la puntuacion que el motor usa ahora (aprendidos o por defecto). */
    public String weights() {
        return EngineStatus.getInstance().track(() -> restTemplate.getForObject(properties.mixUrl("weights"), String.class));
    }

    /** Cuantas mezclas buenas hay para aprender el estilo del DJ, como le va al modelo frente a las reglas y si esta encendido. */
    public String styleModel() {
        return EngineStatus.getInstance().track(() -> restTemplate.getForObject(properties.mixUrl("style-model"), String.class));
    }

    /** Enciende o apaga el modelo del estilo del DJ; devuelve su estado. Apagado, el plan usa solo las reglas. */
    public String setStyleModel(boolean enabled) {
        return post("style-model", EngineRequestBuilder.create().with("enabled", enabled).build());
    }

    private String post(String path, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return EngineStatus.getInstance().track(
                () -> restTemplate.postForObject(properties.mixUrl(path), new HttpEntity<>(body, headers), String.class));
    }
}
