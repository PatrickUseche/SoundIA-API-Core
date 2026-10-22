package com.soundia.queue;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import com.soundia.track.TrackClient;

/** La cola con el contexto real y H2; solo el motor de IA (TrackClient) esta simulado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QueueFlowIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackClient tracks;

    private long sesion;

    @BeforeEach
    void iniciarSesion() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Viernes\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        sesion = Long.parseLong(cuerpo.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private ResultActions encolar(long pista) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions/" + sesion + "/queue")
                .contentType(MediaType.APPLICATION_JSON).content("{\"trackId\":" + pista + "}"));
    }

    private long encolarYObtenerId(long pista) throws Exception {
        String cuerpo = encolar(pista).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(cuerpo.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void laColaSigueElOrdenFifoYCadaSiguienteLaVaVaciando() throws Exception {
        encolar(10).andExpect(status().isCreated());
        encolar(20).andExpect(status().isCreated());
        encolar(30).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/sessions/" + sesion + "/queue"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].trackId").value(10))
                .andExpect(jsonPath("$[2].trackId").value(30));

        mockMvc.perform(post("/api/v1/sessions/" + sesion + "/queue/next"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trackId").value(10));
        mockMvc.perform(post("/api/v1/sessions/" + sesion + "/queue/next"))
                .andExpect(jsonPath("$.trackId").value(20));

        mockMvc.perform(get("/api/v1/sessions/" + sesion + "/queue"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].trackId").value(30));
    }

    @Test
    void sacarDeUnaColaVaciaDa404() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/" + sesion + "/queue/next"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("vacia")));
    }

    @Test
    void sePuedeQuitarUnElementoDelMedio() throws Exception {
        encolar(10);
        long medio = encolarYObtenerId(20);
        encolar(30);

        mockMvc.perform(delete("/api/v1/sessions/" + sesion + "/queue/" + medio)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/sessions/" + sesion + "/queue"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].trackId").value(10))
                .andExpect(jsonPath("$[1].trackId").value(30));
    }

    @Test
    void quitarUnElementoInexistenteDa404() throws Exception {
        mockMvc.perform(delete("/api/v1/sessions/" + sesion + "/queue/99999")).andExpect(status().isNotFound());
    }

    @Test
    void siLaPistaNoExisteEnElMotorNoSeEncolaYElClienteRecibeSuMensaje() throws Exception {
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(),
                "{\"error\":\"Pista no encontrada\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                .when(tracks).getTrack(999L);

        encolar(999).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Pista no encontrada"));

        mockMvc.perform(get("/api/v1/sessions/" + sesion + "/queue")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void trasCerrarLaSesionYaNoSePuedeEncolar() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/" + sesion + "/end")).andExpect(status().isOk());

        encolar(10).andExpect(status().isConflict());
    }

    @Test
    void laColaDeUnaSesionInexistenteDa404() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/987654/queue")).andExpect(status().isNotFound());
    }
}
