package com.soundia.session.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.soundia.config.ConflictException;
import com.soundia.config.ResourceNotFoundException;
import com.soundia.session.model.DjSession;
import com.soundia.session.service.DjSessionService;
import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.repository.DjSessionLogRepository;

@WebMvcTest(DjSessionController.class)
class DjSessionControllerTests {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 21, 20, 0);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DjSessionService sessions;

    @MockitoBean
    private DjSessionLogRepository logs;

    private DjSession sesion(long id, String nombre, boolean cerrada) {
        DjSession sesion = new DjSession(nombre, INICIO);
        sesion.setId(id);
        if (cerrada) {
            sesion.end(INICIO.plusHours(2));
        }
        return sesion;
    }

    private ResultActions iniciar(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/sessions").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void iniciarUnaSesionDevuelve201ConLocationYElCuerpo() throws Exception {
        when(sessions.start("Viernes")).thenReturn(sesion(7, "Viernes", false));

        iniciar("{\"name\":\"Viernes\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/sessions/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Viernes"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.endedAt").doesNotExist());
    }

    @Test
    void iniciarSinNombreOEnBlancoDevuelve400() throws Exception {
        iniciar("{}").andExpect(status().isBadRequest());
        iniciar("{\"name\":\"   \"}").andExpect(status().isBadRequest());
    }

    @Test
    void iniciarConUnNombreMuyLargoDevuelve400() throws Exception {
        iniciar("{\"name\":\"" + "x".repeat(101) + "\"}").andExpect(status().isBadRequest());
    }

    @Test
    void iniciarConOtraSesionAbiertaDevuelve409() throws Exception {
        when(sessions.start("Otra")).thenThrow(new ConflictException("Ya hay una sesion abierta (id 3)."));

        iniciar("{\"name\":\"Otra\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya hay una sesion abierta (id 3)."));
    }

    @Test
    void listaLasSesionesRecientes() throws Exception {
        when(sessions.recent()).thenReturn(List.of(sesion(2, "B", true), sesion(1, "A", true)));

        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("B"))
                .andExpect(jsonPath("$[0].active").value(false));
    }

    @Test
    void devuelveLaSesionActiva() throws Exception {
        when(sessions.getActive()).thenReturn(sesion(4, "En vivo", false));

        mockMvc.perform(get("/api/v1/sessions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    void sinSesionActivaDevuelve404() throws Exception {
        when(sessions.getActive()).thenThrow(new ResourceNotFoundException("No hay ninguna sesion abierta."));

        mockMvc.perform(get("/api/v1/sessions/active")).andExpect(status().isNotFound());
    }

    @Test
    void devuelveUnaSesionPorId() throws Exception {
        when(sessions.get(5L)).thenReturn(sesion(5, "Sabado", true));

        mockMvc.perform(get("/api/v1/sessions/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sabado"))
                .andExpect(jsonPath("$.endedAt").exists());
    }

    @Test
    void unaSesionInexistenteDevuelve404() throws Exception {
        when(sessions.get(9L)).thenThrow(new ResourceNotFoundException("No existe la sesion 9."));

        mockMvc.perform(get("/api/v1/sessions/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No existe la sesion 9."));
    }

    @Test
    void cerrarUnaSesionLaDevuelveCerrada() throws Exception {
        when(sessions.end(5L)).thenReturn(sesion(5, "Sabado", true));

        mockMvc.perform(post("/api/v1/sessions/5/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void cerrarUnaSesionYaCerradaDevuelve409() throws Exception {
        when(sessions.end(5L)).thenThrow(new ConflictException("La sesion 5 ya estaba cerrada."));

        mockMvc.perform(post("/api/v1/sessions/5/end")).andExpect(status().isConflict());
    }

    @Test
    void unIdQueNoEsNumericoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void devuelveLosEventosDeUnaSesion() throws Exception {
        when(sessions.get(5L)).thenReturn(sesion(5, "Sabado", true));
        when(logs.findTop500BySessionIdOrderByCreatedInAscIdAsc(5L))
                .thenReturn(List.of(new DjSessionLog(14, 127, "Corte de EQ"), new DjSessionLog(15, 64)));

        mockMvc.perform(get("/api/v1/sessions/5/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].controlId").value(14))
                .andExpect(jsonPath("$[0].action").value("Corte de EQ"))
                .andExpect(jsonPath("$[1].action").doesNotExist());
    }

    @Test
    void losEventosDeUnaSesionInexistenteDan404() throws Exception {
        when(sessions.get(9L)).thenThrow(new ResourceNotFoundException("No existe la sesion 9."));

        mockMvc.perform(get("/api/v1/sessions/9/logs")).andExpect(status().isNotFound());
    }
}
