package com.soundia.telemetry.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import com.soundia.telemetry.pattern.DjControllerReader;
import com.soundia.telemetry.repository.DjSessionLogRepository;
import com.soundia.telemetry.service.MidiEventService;

@WebMvcTest(MidiTelemetryController.class)
class ApiExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DjControllerReader reader;

    @MockitoBean
    private DjSessionLogRepository repository;

    @MockitoBean
    private MidiEventService midiEventService;

    private ResultActions enviar(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void validacionFallidaDevuelve400ConElDetalleDeCadaCampo() throws Exception {
        enviar("/api/v1/midi/event", "{\"controlId\":14,\"value\":200}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Validacion fallida"))
                .andExpect(jsonPath("$.detalles", hasItem("value: value debe estar entre 0 y 127")));
    }

    @Test
    void jsonIlegibleDevuelve400SinFiltrarDetallesInternos() throws Exception {
        enviar("/api/v1/midi/event", "{no es json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El cuerpo de la peticion no es un JSON valido"))
                .andExpect(content().string(not(containsString("Jackson"))));
    }

    @Test
    void motorDeIaCaidoDevuelve502ConMensajeGenerico() throws Exception {
        when(midiEventService.decideAndStore(14, 127))
                .thenThrow(new ResourceAccessException("Connection refused: localhost:8000"));

        enviar("/api/v1/midi/ai/predict", "{\"controlId\":14,\"value\":127}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("El motor de IA no esta disponible"))
                .andExpect(content().string(not(containsString("localhost"))));
    }

    @Test
    void unaRutaInexistenteDevuelve404ConElCuerpoUniforme() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
    }

    @Test
    void unMetodoHttpNoPermitidoDevuelve405() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/midi/ai/predict"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("Metodo no permitido"));
    }

    @Test
    void unTipoDeContenidoNoSoportadoDevuelve415() throws Exception {
        mockMvc.perform(post("/api/v1/midi/event").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value("Tipo de contenido no soportado"));
    }

    @Test
    void errorInesperadoDevuelve500SinFiltrarElMensaje() throws Exception {
        when(midiEventService.decideAndStore(14, 127)).thenThrow(new IllegalStateException("clave secreta en el mensaje"));

        enviar("/api/v1/midi/ai/predict", "{\"controlId\":14,\"value\":127}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Error interno del servidor"))
                .andExpect(content().string(not(containsString("secreta"))));
    }
}
