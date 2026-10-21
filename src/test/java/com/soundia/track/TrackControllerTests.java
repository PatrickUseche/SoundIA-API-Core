package com.soundia.track;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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

@WebMvcTest(TrackController.class)
class TrackControllerTests {

    private static final String PISTA = "{\"id\":1,\"title\":\"tema.wav\",\"bpm\":128.0,\"camelot\":\"8B\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackClient tracks;

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
}
