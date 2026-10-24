package com.soundia.track;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.soundia.queue.service.QueueService;
import org.springframework.http.ResponseEntity;

@WebMvcTest(TrackController.class)
class TrackControllerTests {

    private static final String PISTA = "{\"id\":1,\"title\":\"tema.wav\",\"bpm\":128.0,\"camelot\":\"8B\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackClient tracks;

    @MockitoBean
    private QueueService queues;

    private MockMultipartFile audio(String nombre, byte[] contenido) {
        return new MockMultipartFile("file", nombre, "audio/wav", contenido);
    }

    @Test
    void analizarUnaPistaDevuelve201ConElJsonDeDjango() throws Exception {
        when(tracks.analyze(eq("tema.wav"), eq("Mi tema"), any())).thenReturn(PISTA);

        mockMvc.perform(multipart("/api/v1/tracks/analyze").file(audio("tema.wav", new byte[] {1, 2, 3}))
                        .param("title", "Mi tema"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.camelot").value("8B"))
                .andExpect(jsonPath("$.bpm").value(128.0));
    }

    @Test
    void elTituloEsOpcional() throws Exception {
        when(tracks.analyze(eq("tema.wav"), eq(null), any())).thenReturn(PISTA);

        mockMvc.perform(multipart("/api/v1/tracks/analyze").file(audio("tema.wav", new byte[] {1})))
                .andExpect(status().isCreated());
    }

    @Test
    void reenviaLosBytesDelArchivo() throws Exception {
        when(tracks.analyze(any(), any(), any())).thenReturn(PISTA);

        mockMvc.perform(multipart("/api/v1/tracks/analyze").file(audio("tema.wav", new byte[] {9, 8, 7})))
                .andExpect(status().isCreated());

        verify(tracks).analyze(eq("tema.wav"), eq(null), org.mockito.ArgumentMatchers.argThat(
                bytes -> bytes.length == 3 && bytes[0] == 9 && bytes[2] == 7));
    }

    @Test
    void sinArchivoDevuelve400() throws Exception {
        mockMvc.perform(multipart("/api/v1/tracks/analyze").param("title", "x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Falta el campo 'file' en la peticion"));

        verify(tracks, never()).analyze(any(), any(), any());
    }

    @Test
    void unArchivoVacioDevuelve400() throws Exception {
        mockMvc.perform(multipart("/api/v1/tracks/analyze").file(audio("vacio.wav", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El archivo de audio esta vacio."));

        verify(tracks, never()).analyze(any(), any(), any());
    }

    @Test
    void unArchivoQueDjangoNoPuedeAnalizarDevuelveSuCodigoYMensaje() throws Exception {
        when(tracks.analyze(any(), any(), any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable", new org.springframework.http.HttpHeaders(),
                "{\"error\":\"El audio es silencio; no hay nada que analizar.\"}".getBytes(),
                java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/tracks/analyze").file(audio("silencio.wav", new byte[] {0, 0})))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("El audio es silencio; no hay nada que analizar."));
    }

    @Test
    void siDjangoNoRespondeDevuelve502() throws Exception {
        when(tracks.analyze(any(), any(), any())).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(multipart("/api/v1/tracks/analyze").file(audio("tema.wav", new byte[] {1})))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("El motor de IA no esta disponible"));
    }

    @Test
    void listaLasPistas() throws Exception {
        when(tracks.listTracks()).thenReturn("{\"tracks\":[" + PISTA + "]}");

        mockMvc.perform(get("/api/v1/tracks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tracks[0].id").value(1));
    }

    @Test
    void devuelveUnaPistaPorId() throws Exception {
        when(tracks.getTrack(1)).thenReturn(PISTA);

        mockMvc.perform(get("/api/v1/tracks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("tema.wav"));
    }

    @Test
    void unIdInexistenteEnDjangoDevuelve404() throws Exception {
        when(tracks.getTrack(99)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new org.springframework.http.HttpHeaders(),
                "{\"error\":\"Pista no encontrada\"}".getBytes(), java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/tracks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Pista no encontrada"));
    }

    @Test
    void unIdNoNumericoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/tracks/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void unArchivoQueSuperaElTamanoMaximoDevuelve413() throws Exception {
        when(tracks.analyze(any(), any(), any())).thenThrow(new MaxUploadSizeExceededException(1024));

        mockMvc.perform(multipart("/api/v1/tracks/analyze").file(audio("enorme.wav", new byte[] {1})))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("El archivo supera el tamano maximo permitido"));
    }

    @Test
    void devuelveElAudioDeUnaPistaConSuTipoYLongitud() throws Exception {
        byte[] audio = {1, 2, 3, 4, 5};
        when(tracks.getTrackAudio(4)).thenReturn(
                ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav")).body(audio));

        mockMvc.perform(get("/api/v1/tracks/4/audio"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Length", "5"))
                .andExpect(content().bytes(audio));
    }

    @Test
    void sinTipoDeContenidoDelMotorUsaOctetStream() throws Exception {
        when(tracks.getTrackAudio(4)).thenReturn(ResponseEntity.ok().body(new byte[] {9}));

        mockMvc.perform(get("/api/v1/tracks/4/audio"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM));
    }

    @Test
    void unaPistaSinAudioDevuelve404ConElMensajeDelMotor() throws Exception {
        when(tracks.getTrackAudio(4)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new org.springframework.http.HttpHeaders(),
                "{\"error\":\"La pista no tiene audio guardado.\"}".getBytes(), java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/tracks/4/audio"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("La pista no tiene audio guardado."));
    }

    @Test
    void fijaElPrimerTiempoYDevuelveLaPistaDeDjango() throws Exception {
        when(tracks.setDownbeat(1, 4.6)).thenReturn(PISTA);

        mockMvc.perform(put("/api/v1/tracks/1/grid").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"downbeat\":4.6}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void elPrimerTiempoAceptaUnEntero() throws Exception {
        when(tracks.setDownbeat(1, 4.0)).thenReturn(PISTA);

        mockMvc.perform(put("/api/v1/tracks/1/grid").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"downbeat\":4}"))
                .andExpect(status().isOk());
    }

    @Test
    void unPrimerTiempoInvalidoDevuelve400SinLlamarAlMotor() throws Exception {
        for (String cuerpo : new String[] {"{}", "{\"downbeat\":null}", "{\"downbeat\":-1}",
                "{\"downbeat\":true}", "{\"downbeat\":\"abc\"}", "no es json", ""}) {
            mockMvc.perform(put("/api/v1/tracks/1/grid").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }

        verify(tracks, never()).setDownbeat(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void siLaPistaNoTieneRejillaDevuelve409ConElMensajeDeDjango() throws Exception {
        when(tracks.setDownbeat(1, 4.6)).thenThrow(HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", new org.springframework.http.HttpHeaders(),
                "{\"error\":\"La pista no tiene una rejilla de pulsos (no tiene un ritmo claro)\"}".getBytes(),
                java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(put("/api/v1/tracks/1/grid").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"downbeat\":4.6}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("La pista no tiene una rejilla de pulsos (no tiene un ritmo claro)"));
    }

    @Test
    void unaPistaInexistenteEnDjangoDevuelve404() throws Exception {
        when(tracks.setDownbeat(99, 4.6)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new org.springframework.http.HttpHeaders(),
                "{\"error\":\"Pista no encontrada\"}".getBytes(), java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(put("/api/v1/tracks/99/grid").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"downbeat\":4.6}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void borrarUnaPistaDevuelve204YLaQuitaDeLasColas() throws Exception {
        mockMvc.perform(delete("/api/v1/tracks/7"))
                .andExpect(status().isNoContent());

        verify(tracks).deleteTrack(7);
        verify(queues).removeTrack(7L);
    }

    @Test
    void borrarUnaPistaInexistenteDevuelve404YNoTocaLasColas() throws Exception {
        doThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new org.springframework.http.HttpHeaders(),
                "{\"error\":\"Pista no encontrada\"}".getBytes(), java.nio.charset.StandardCharsets.UTF_8))
                .when(tracks).deleteTrack(99);

        mockMvc.perform(delete("/api/v1/tracks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Pista no encontrada"));

        verify(queues, never()).removeTrack(any());
    }

    @Test
    void siElMotorFallaAlBorrarDevuelve502YConservaLasColas() throws Exception {
        doThrow(new ResourceAccessException("Connection refused")).when(tracks).deleteTrack(1);

        mockMvc.perform(delete("/api/v1/tracks/1"))
                .andExpect(status().isBadGateway());

        verify(queues, never()).removeTrack(any());
    }

    @Test
    void siDjangoNoRespondeAlFijarElPrimerTiempoDevuelve502() throws Exception {
        when(tracks.setDownbeat(1, 4.6)).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(put("/api/v1/tracks/1/grid").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"downbeat\":4.6}"))
                .andExpect(status().isBadGateway());
    }

}
