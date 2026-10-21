package com.soundia.telemetry.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.soundia.telemetry.stream.LiveStreamService;
import com.soundia.telemetry.stream.StreamEventType;

@WebMvcTest(LiveStreamController.class)
@Import(LiveStreamService.class)
class LiveStreamControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiveStreamService liveStream;

    @Test
    void abreUnaConexionSseYRegistraAlCliente() throws Exception {
        int antes = liveStream.connectedClients();

        mockMvc.perform(get("/api/v1/midi/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());

        // El servicio es compartido entre tests: se compara contra el valor previo.
        assertEquals(antes + 1, liveStream.connectedClients());
    }

    @Test
    void losEventosPublicadosLlegaranAlClienteConectado() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/midi/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        liveStream.publish(StreamEventType.MIDI, java.util.Map.of("controlId", 14, "value", 127));

        String cuerpo = result.getResponse().getContentAsString();
        assertTrue(cuerpo.contains("event:midi"), cuerpo);
        assertTrue(cuerpo.contains("\"controlId\":14"), cuerpo);
        assertTrue(cuerpo.contains("\"value\":127"), cuerpo);
    }

    @Test
    void respondeConContentTypeEventStream() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/midi/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andReturn();

        assertTrue(result.getResponse().getContentType().startsWith("text/event-stream"));
    }

    @Test
    void elLatidoLlegaComoComentarioSseSinSerUnEvento() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/midi/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        liveStream.sendHeartbeat();

        String cuerpo = result.getResponse().getContentAsString();
        assertTrue(cuerpo.contains(":keep-alive"), cuerpo);
        assertTrue(!cuerpo.contains("event:keep-alive"), cuerpo);
    }
}
