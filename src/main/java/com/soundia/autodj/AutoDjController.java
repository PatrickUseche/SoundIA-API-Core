package com.soundia.autodj;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    private static ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
