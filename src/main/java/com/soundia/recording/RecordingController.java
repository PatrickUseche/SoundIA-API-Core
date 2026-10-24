package com.soundia.recording;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.soundia.config.BadRequestException;

/**
 * Grabaciones de mezclas: lo que el DJ hace al pasar de una pista a otra, para aprender su estilo. Los datos viven en
 * el motor de IA; este controlador es solo la pasarela.
 */
@RestController
@RequestMapping("/api/v1/recordings")
public class RecordingController {

    /** Una grabacion con todas sus curvas pesa unas decenas de KB; esto corta cuerpos absurdos antes de reenviarlos. */
    static final int MAX_BODY_CHARS = 256 * 1024;

    /** Una copia con muchas grabaciones es mas grande; el motor de IA acepta hasta 2,5 MB. */
    static final int MAX_IMPORT_CHARS = 2 * 1024 * 1024;

    private final RecordingClient recordings;

    public RecordingController(RecordingClient recordings) {
        this.recordings = recordings;
    }

    /** Guarda una transicion (ver el README del motor de IA para los campos). 201 con la grabacion y sus rasgos. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(@RequestBody String body) {
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                .body(recordings.create(checked(body)));
    }

    @GetMapping
    public ResponseEntity<String> list(@RequestParam(value = "quality", required = false) String quality) {
        return json(recordings.list(quality));
    }

    /** Copia de seguridad: todas las grabaciones en un archivo JSON descargable. */
    @GetMapping("/export")
    public ResponseEntity<String> export() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header("Content-Disposition", "attachment; filename=\"soundia-grabaciones.json\"")
                .body(recordings.exportAll());
    }

    /** Restaura una copia exportada (todo o nada, sin duplicar las que ya estan). Responde `{created, skipped}`. */
    @PostMapping(path = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> importBackup(@RequestBody String body) {
        return json(recordings.importAll(checked(body, MAX_IMPORT_CHARS, "La copia es demasiado grande.")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> get(@PathVariable long id) {
        return json(recordings.get(id));
    }

    /** La revision del DJ: `{quality?, notes?}`. */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> review(@PathVariable long id, @RequestBody String body) {
        return json(recordings.review(id, checked(body)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        recordings.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static String checked(String body) {
        return checked(body, MAX_BODY_CHARS, "La grabacion es demasiado grande.");
    }

    private static String checked(String body, int maxChars, String tooBig) {
        if (body == null || body.isBlank()) {
            throw new BadRequestException("El cuerpo de la peticion esta vacio.");
        }
        if (body.length() > maxChars) {
            throw new BadRequestException(tooBig);
        }
        return body;
    }

    private static ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
