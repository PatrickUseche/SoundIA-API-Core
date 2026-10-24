package com.soundia.recording;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@WebMvcTest(RecordingController.class)
class RecordingControllerTests {

    private static final String GRABACION = "{\"id\":3,\"quality\":\"unreviewed\"}";
    private static final String CUERPO = "{\"fromTrackId\":1,\"toTrackId\":2,\"overlapSeconds\":32}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecordingClient recordings;

    private static HttpClientErrorException errorDeDjango(HttpStatus status, String mensaje) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(), new HttpHeaders(),
                ("{\"error\":\"" + mensaje + "\"}").getBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void guardaUnaGrabacionYDevuelve201ConElJsonDeDjango() throws Exception {
        when(recordings.create(CUERPO)).thenReturn(GRABACION);

        mockMvc.perform(post("/api/v1/recordings").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(3));
    }

    @Test
    void exportaLaCopiaComoArchivoDescargable() throws Exception {
        when(recordings.exportAll()).thenReturn("{\"format\":\"soundia-recordings\",\"recordings\":[]}");

        mockMvc.perform(get("/api/v1/recordings/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.format").value("soundia-recordings"));
    }

    @Test
    void exportarNoSeConfundeConLaRutaDeUnaGrabacion() throws Exception {
        when(recordings.exportAll()).thenReturn("{}");

        mockMvc.perform(get("/api/v1/recordings/export")).andExpect(status().isOk());

        verify(recordings, never()).get(anyLong());
    }

    @Test
    void restauraUnaCopiaYDevuelveLoQueDiceElMotor() throws Exception {
        when(recordings.importAll(CUERPO)).thenReturn("{\"created\":2,\"skipped\":1}");

        mockMvc.perform(post("/api/v1/recordings/import").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void unaCopiaGrandePeroRazonableSeReenviaAunqueUnaGrabacionSolaNoPodria() throws Exception {
        String grande = "{\"x\":\"" + "a".repeat(RecordingController.MAX_BODY_CHARS + 10) + "\"}";
        when(recordings.importAll(any())).thenReturn("{\"created\":0,\"skipped\":0}");

        mockMvc.perform(post("/api/v1/recordings/import").contentType(MediaType.APPLICATION_JSON).content(grande))
                .andExpect(status().isOk());
    }

    @Test
    void unaCopiaAbsurdamenteGrandeDevuelve400SinReenviarla() throws Exception {
        String enorme = "{\"x\":\"" + "a".repeat(RecordingController.MAX_IMPORT_CHARS + 10) + "\"}";

        mockMvc.perform(post("/api/v1/recordings/import").contentType(MediaType.APPLICATION_JSON).content(enorme))
                .andExpect(status().isBadRequest());

        verify(recordings, never()).importAll(any());
    }

    @Test
    void restaurarConCuerpoVacioDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/recordings/import").contentType(MediaType.APPLICATION_JSON).content(" "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void siElMotorRechazaLaCopiaDevuelveSuMensaje() throws Exception {
        when(recordings.importAll(any())).thenThrow(errorDeDjango(HttpStatus.BAD_REQUEST, "Grabacion 2: mal"));

        mockMvc.perform(post("/api/v1/recordings/import").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Grabacion 2: mal"));
    }

    @Test
    void unCuerpoVacioDevuelve400SinLlamarAlMotor() throws Exception {
        mockMvc.perform(post("/api/v1/recordings").contentType(MediaType.APPLICATION_JSON).content("   "))
                .andExpect(status().isBadRequest());

        verify(recordings, never()).create(any());
    }

    @Test
    void unCuerpoAbsurdamenteGrandeDevuelve400SinReenviarlo() throws Exception {
        String enorme = "{\"notes\":\"" + "x".repeat(RecordingController.MAX_BODY_CHARS) + "\"}";

        mockMvc.perform(post("/api/v1/recordings").contentType(MediaType.APPLICATION_JSON).content(enorme))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La grabacion es demasiado grande."));

        verify(recordings, never()).create(any());
    }

    @Test
    void sinContentTypeJsonDevuelve415() throws Exception {
        mockMvc.perform(post("/api/v1/recordings").contentType(MediaType.TEXT_PLAIN).content(CUERPO))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void siDjangoRechazaLaGrabacionDevuelveSuCodigoYMensaje() throws Exception {
        when(recordings.create(any())).thenThrow(errorDeDjango(HttpStatus.BAD_REQUEST, "overlapSeconds debe ser mayor que 0."));

        mockMvc.perform(post("/api/v1/recordings").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("overlapSeconds debe ser mayor que 0."));
    }

    @Test
    void siFaltaUnaPistaDevuelve404() throws Exception {
        when(recordings.create(any())).thenThrow(errorDeDjango(HttpStatus.NOT_FOUND, "No existe una de las pistas de la mezcla."));

        mockMvc.perform(post("/api/v1/recordings").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isNotFound());
    }

    @Test
    void siDjangoNoRespondeDevuelve502() throws Exception {
        when(recordings.create(any())).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(post("/api/v1/recordings").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isBadGateway());
    }

    @Test
    void listaSinFiltroYFiltrandoPorCalidad() throws Exception {
        when(recordings.list(null)).thenReturn("{\"recordings\":[],\"counts\":{\"total\":0}}");
        when(recordings.list("good")).thenReturn("{\"recordings\":[" + GRABACION + "],\"counts\":{\"total\":1}}");

        mockMvc.perform(get("/api/v1/recordings"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.counts.total").value(0));
        mockMvc.perform(get("/api/v1/recordings").param("quality", "good"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recordings[0].id").value(3));
    }

    @Test
    void devuelveUnaGrabacionPorId() throws Exception {
        when(recordings.get(3)).thenReturn(GRABACION);

        mockMvc.perform(get("/api/v1/recordings/3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quality").value("unreviewed"));
    }

    @Test
    void unaGrabacionInexistenteDevuelve404() throws Exception {
        when(recordings.get(99)).thenThrow(errorDeDjango(HttpStatus.NOT_FOUND, "Grabacion no encontrada"));

        mockMvc.perform(get("/api/v1/recordings/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Grabacion no encontrada"));
    }

    @Test
    void unIdQueNoEsUnNumeroDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/recordings/abc")).andExpect(status().isBadRequest());

        verify(recordings, never()).get(anyLong());
    }

    @Test
    void revisaUnaGrabacion() throws Exception {
        when(recordings.review(eq(3L), eq("{\"quality\":\"good\"}"))).thenReturn("{\"id\":3,\"quality\":\"good\"}");

        mockMvc.perform(put("/api/v1/recordings/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quality\":\"good\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quality").value("good"));
    }

    @Test
    void revisarConUnCuerpoVacioDevuelve400() throws Exception {
        mockMvc.perform(put("/api/v1/recordings/3").contentType(MediaType.APPLICATION_JSON).content(" "))
                .andExpect(status().isBadRequest());

        verify(recordings, never()).review(anyLong(), any());
    }

    @Test
    void borraUnaGrabacionYDevuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/recordings/3")).andExpect(status().isNoContent());

        verify(recordings).delete(3);
    }

    @Test
    void borrarUnaGrabacionInexistenteDevuelve404() throws Exception {
        org.mockito.Mockito.doThrow(errorDeDjango(HttpStatus.NOT_FOUND, "Grabacion no encontrada"))
                .when(recordings).delete(99);

        mockMvc.perform(delete("/api/v1/recordings/99")).andExpect(status().isNotFound());
    }
}
