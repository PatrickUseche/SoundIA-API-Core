package com.soundia.telemetry.stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.ResourceAccessException;

import com.soundia.telemetry.dto.AiDecisionDto;
import com.soundia.telemetry.service.IaEngineClient;

/** Flujo completo con el contexto real y H2: POST /event -> observadores -> IA -> BD -> SSE. */
@SpringBootTest(properties = "soundia.ia-engine.retry-delay-ms=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LiveStreamIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IaEngineClient iaEngine;

    private MvcResult conectarCliente() throws Exception {
        return mockMvc.perform(get("/api/v1/midi/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private void enviarEvento(String json) throws Exception {
        mockMvc.perform(post("/api/v1/midi/event").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    /** El procesamiento es asincrono: se espera hasta 5 s a que el cuerpo contenga el texto. */
    private String esperarContenido(MvcResult cliente, String texto) throws Exception {
        long limite = System.currentTimeMillis() + 5000;
        String cuerpo = "";
        while (System.currentTimeMillis() < limite) {
            cuerpo = cliente.getResponse().getContentAsString();
            if (cuerpo.contains(texto)) {
                return cuerpo;
            }
            Thread.sleep(25);
        }
        return cuerpo;
    }

    @Test
    void elClienteRecibeElEventoMidiYLaDecisionDeLaIa() throws Exception {
        when(iaEngine.calculateMix(14, 127)).thenReturn(new AiDecisionDto("success", "Corte de EQ en deck 14"));
        MvcResult cliente = conectarCliente();

        enviarEvento("{\"controlId\":14,\"value\":127}");

        String cuerpo = esperarContenido(cliente, "Corte de EQ en deck 14");
        assertTrue(cuerpo.contains("event:midi"), cuerpo);
        assertTrue(cuerpo.contains("event:decision"), cuerpo);
        assertTrue(cuerpo.contains("\"status\":\"success\""), cuerpo);
        assertTrue(cuerpo.contains("\"action\":\"Corte de EQ en deck 14\""), cuerpo);
    }

    @Test
    void siLaIaNoResponde_elClienteRecibeLaDecisionConEstadoUnavailable() throws Exception {
        when(iaEngine.calculateMix(15, 64)).thenThrow(new ResourceAccessException("Django caido"));
        MvcResult cliente = conectarCliente();

        enviarEvento("{\"controlId\":15,\"value\":64}");

        String cuerpo = esperarContenido(cliente, "unavailable");
        assertTrue(cuerpo.contains("\"status\":\"unavailable\""), cuerpo);
        assertTrue(cuerpo.contains("\"controlId\":15"), cuerpo);
    }
}
