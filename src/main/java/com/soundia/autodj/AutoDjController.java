package com.soundia.autodj;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soundia.autodj.dto.FeedbackRequest;
import com.soundia.autodj.dto.MixTimingRequest;
import com.soundia.autodj.dto.NextTracksRequest;
import com.soundia.autodj.dto.TransitionRequest;
import com.soundia.config.BadRequestException;

import jakarta.validation.Valid;

/** Auto-DJ: recomendaciones de la IA sobre que pista sigue y como mezclarla. */
@RestController
@RequestMapping("/api/v1/autodj")
public class AutoDjController {

    private final AutoDjClient autoDj;

    public AutoDjController(AutoDjClient autoDj) {
        this.autoDj = autoDj;
    }

    /** Las pistas que mejor siguen a la indicada, de mayor a menor compatibilidad. */
    @PostMapping("/next")
    public ResponseEntity<String> next(@Valid @RequestBody NextTracksRequest request) {
        return json(autoDj.nextTracks(request.trackId(), request.limit()));
    }

    /** Plan de transicion entre dos pistas: estilo, compases, puntos de mezcla y curvas de EQ. */
    @PostMapping("/transition")
    public ResponseEntity<String> transition(@Valid @RequestBody TransitionRequest request) {
        if (request.fromTrackId().equals(request.toTrackId())) {
            throw new BadRequestException("No se puede hacer una transicion de una pista a si misma.");
        }
        return json(autoDj.transition(request.fromTrackId(), request.toTrackId()));
    }

    /**
     * El DJ valora una transicion (me gusto / no me gusto). El motor guarda la valoracion y reajusta cuanto
     * pesan tempo, armonia y energia en sus recomendaciones. Devuelve los pesos vigentes.
     */
    @PostMapping("/feedback")
    public ResponseEntity<String> feedback(@Valid @RequestBody FeedbackRequest request) {
        if (request.fromTrackId().equals(request.toTrackId())) {
            throw new BadRequestException("No se puede valorar una transicion de una pista a si misma.");
        }
        String body = autoDj.submitFeedback(request.fromTrackId(), request.toTrackId(), request.liked());
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /**
     * El DJ pidio mezclar antes de lo que el Auto-DJ habia planeado. El motor guarda el caso y, con suficientes,
     * adelanta sus mezclas. Devuelve cuantos casos lleva y el adelanto vigente.
     */
    @PostMapping("/timing")
    public ResponseEntity<String> timing(@Valid @RequestBody MixTimingRequest request) {
        if (request.fromTrackId().equals(request.toTrackId())) {
            throw new BadRequestException("No se puede mezclar una pista consigo misma.");
        }
        String body = autoDj.submitMixTiming(request.fromTrackId(), request.toTrackId(),
                request.plannedMixOutAt(), request.actualMixOutAt());
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** Los pesos de la puntuacion que usa el motor ahora, si son aprendidos y con cuantas valoraciones. */
    @GetMapping("/weights")
    public ResponseEntity<String> weights() {
        return json(autoDj.weights());
    }

    private static ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
