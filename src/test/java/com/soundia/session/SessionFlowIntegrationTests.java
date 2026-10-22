package com.soundia.session;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.soundia.telemetry.dto.AiDecisionDto;
import com.soundia.telemetry.service.IaEngineClient;

/** Flujo completo de una sesion con el contexto real y H2: iniciar, mandar eventos, consultar y cerrar. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional // cada prueba revierte lo que guarda, asi no se contaminan entre si
class SessionFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IaEngineClient iaEngine;

    private ResultActions iniciar(String nombre) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + nombre + "\"}"));
    }

    /** /ai/predict es sincrono: el evento queda guardado cuando responde. */
    private void enviarEvento(int controlId, int value) throws Exception {
        mockMvc.perform(post("/api/v1/midi/ai/predict").contentType(MediaType.APPLICATION_JSON)
                .content("{\"controlId\":" + controlId + ",\"value\":" + value + "}"))
                .andExpect(status().isOk());
    }

    private long idDe(ResultActions respuesta) throws Exception {
        String cuerpo = respuesta.andReturn().getResponse().getContentAsString();
        return Long.parseLong(cuerpo.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void losEventosDuranteLaSesionQuedanAsociadosYLosPosterioresNo() throws Exception {
        when(iaEngine.calculateMix(14, 127)).thenReturn(new AiDecisionDto("success", "Corte de EQ"));
        when(iaEngine.calculateMix(15, 64)).thenReturn(new AiDecisionDto("success", "Subir agudos"));

        long sesion = idDe(iniciar("Viernes").andExpect(status().isCreated()));
        enviarEvento(14, 127);
        mockMvc.perform(post("/api/v1/sessions/" + sesion + "/end")).andExpect(status().isOk());
        enviarEvento(15, 64); // despues de cerrar: no pertenece a la sesion

        mockMvc.perform(get("/api/v1/sessions/" + sesion + "/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].controlId").value(14))
                .andExpect(jsonPath("$[0].action").value("Corte de EQ"))
                .andExpect(jsonPath("$[0].sessionId").value(sesion));
    }

    @Test
    void noSePuedeIniciarOtraSesionMientrasHayUnaAbierta() throws Exception {
        long primera = idDe(iniciar("Primera").andExpect(status().isCreated()));

        iniciar("Segunda")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("id " + primera)));
    }

    @Test
    void trasCerrarSePuedeIniciarOtraYLaActivaEsLaNueva() throws Exception {
        long primera = idDe(iniciar("Primera").andExpect(status().isCreated()));
        mockMvc.perform(post("/api/v1/sessions/" + primera + "/end")).andExpect(status().isOk());

        long segunda = idDe(iniciar("Segunda").andExpect(status().isCreated()));

        mockMvc.perform(get("/api/v1/sessions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(segunda))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void cerrarDosVecesLaMismaSesionDa409() throws Exception {
        long sesion = idDe(iniciar("Viernes").andExpect(status().isCreated()));
        mockMvc.perform(post("/api/v1/sessions/" + sesion + "/end")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sessions/" + sesion + "/end")).andExpect(status().isConflict());
    }

    @Test
    void sinSesionAbiertaLaActivaDa404() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/active")).andExpect(status().isNotFound());
    }

    @Test
    void unEventoSinSesionAbiertaSeGuardaIgualConSessionIdNulo() throws Exception {
        when(iaEngine.calculateMix(20, 30)).thenReturn(new AiDecisionDto("success", "x"));

        enviarEvento(20, 30);

        mockMvc.perform(get("/api/v1/midi/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].controlId").value(20))
                .andExpect(jsonPath("$[0].sessionId").doesNotExist());
    }
}
