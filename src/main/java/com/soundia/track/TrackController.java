package com.soundia.track;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.soundia.config.BadRequestException;

/** Biblioteca de pistas. Los datos viven en el motor de IA; este controlador es solo la pasarela. */
@RestController
@RequestMapping("/api/v1/tracks")
public class TrackController {

    private final TrackClient tracks;

    public TrackController(TrackClient tracks) {
        this.tracks = tracks;
    }

    /** Sube un archivo de audio para analizarlo (BPM, tonalidad, energia y puntos de mezcla) y guardarlo. */
    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> analyze(@RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "title", required = false) String title)
            throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("El archivo de audio esta vacio.");
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "audio";
        String body = tracks.analyze(filename, title, file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @GetMapping
    public ResponseEntity<String> list() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(tracks.listTracks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<String> get(@PathVariable long id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(tracks.getTrack(id));
    }

    /** El audio original de la pista, para decodificarlo y reproducirlo en un deck del navegador. */
    @GetMapping("/{id}/audio")
    public ResponseEntity<byte[]> audio(@PathVariable long id) {
        ResponseEntity<byte[]> upstream = tracks.getTrackAudio(id);
        byte[] body = upstream.getBody() != null ? upstream.getBody() : new byte[0];
        MediaType type = upstream.getHeaders().getContentType() != null
                ? upstream.getHeaders().getContentType() : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type).contentLength(body.length).body(body);
    }
}
