package com.soundia.telemetry.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.pattern.DjControllerReader;
import com.soundia.telemetry.repository.DjSessionLogRepository;

@WebMvcTest(MidiTelemetryController.class)
class MidiTelemetryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DjControllerReader reader;

    @MockitoBean
    private DjSessionLogRepository repository;

    private void postEvent(String json, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/midi/event")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void eventoValidoSeNotificaALosObservadores() throws Exception {
        postEvent("{\"controlId\":14,\"value\":127}", 200);

        verify(reader).notifyObservers(any(DjSessionLog.class));
    }

    @Test
    void valorCeroEsValido() throws Exception {
        postEvent("{\"controlId\":14,\"value\":0}", 200);
    }

    @Test
    void valorSobre127SeRechaza() throws Exception {
        postEvent("{\"controlId\":14,\"value\":128}", 400);

        verify(reader, never()).notifyObservers(any());
    }

    @Test
    void valorNegativoSeRechaza() throws Exception {
        postEvent("{\"controlId\":14,\"value\":-1}", 400);

        verify(reader, never()).notifyObservers(any());
    }

    @Test
    void valorNuloSeRechaza() throws Exception {
        postEvent("{\"controlId\":14}", 400);

        verify(reader, never()).notifyObservers(any());
    }

    @Test
    void controlIdNuloSeRechaza() throws Exception {
        postEvent("{\"value\":64}", 400);

        verify(reader, never()).notifyObservers(any());
    }

    @Test
    void controlIdNegativoSeRechaza() throws Exception {
        postEvent("{\"controlId\":-5,\"value\":64}", 400);

        verify(reader, never()).notifyObservers(any());
    }

    @Test
    void cuerpoSinJsonValidoSeRechaza() throws Exception {
        postEvent("{no es json", 400);
    }
}
