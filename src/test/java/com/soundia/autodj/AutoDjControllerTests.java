package com.soundia.autodj;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

@WebMvcTest(AutoDjController.class)
class AutoDjControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutoDjClient autoDj;

    private ResultActions enviar(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void devuelveLasSiguientesPistasDelMotor() throws Exception {
        when(autoDj.nextTracks(7, 3)).thenReturn("{\"candidates\":[{\"track\":{\"id\":2},\"compatibility\":{\"score\":0.93}}]}");

        enviar("/api/v1/autodj/next", "{\"trackId\":7,\"limit\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].track.id").value(2))
                .andExpect(jsonPath("$.candidates[0].compatibility.score").value(0.93));
    }

    @Test
    void elLimiteEsOpcional() throws Exception {
        when(autoDj.nextTracks(7, null)).thenReturn("{\"candidates\":[]}");

        enviar("/api/v1/autodj/next", "{\"trackId\":7}").andExpect(status().isOk());

        verify(autoDj).nextTracks(7, null);
    }

    @Test
    void unaPeticionInvalidaDeSiguientesPistasDevuelve400SinLlamarAlMotor() throws Exception {
        String[] casos = {
            "{}",                                  // falta trackId
            "{\"trackId\":0}",                     // no positivo
            "{\"trackId\":-3}",
            "{\"trackId\":7,\"limit\":0}",         // limite fuera de rango
            "{\"trackId\":7,\"limit\":21}",
            "{\"trackId\":\"siete\"}",             // tipo equivocado
        };
        for (String caso : casos) {
            enviar("/api/v1/autodj/next", caso).andExpect(status().isBadRequest());
        }

        verify(autoDj, never()).nextTracks(anyLong(), any());
    }

    @Test
    void elDetalleDeLaValidacionNombraElCampo() throws Exception {
        enviar("/api/v1/autodj/next", "{\"trackId\":7,\"limit\":99}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0]").value("limit: limit debe estar entre 1 y 20"));
    }

    @Test
    void devuelveElPlanDeTransicionDelMotor() throws Exception {
        when(autoDj.transition(1, 2)).thenReturn("{\"style\":\"blend\",\"bars\":16,\"seconds\":30.0}");

        enviar("/api/v1/autodj/transition", "{\"fromTrackId\":1,\"toTrackId\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.style").value("blend"))
                .andExpect(jsonPath("$.bars").value(16));
    }

    @Test
    void unaTransicionDeUnaPistaConsigoMismaDevuelve400() throws Exception {
        enviar("/api/v1/autodj/transition", "{\"fromTrackId\":5,\"toTrackId\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No se puede hacer una transicion de una pista a si misma."));

        verify(autoDj, never()).transition(anyLong(), anyLong());
    }

    @Test
    void unaTransicionInvalidaDevuelve400() throws Exception {
        enviar("/api/v1/autodj/transition", "{\"fromTrackId\":1}").andExpect(status().isBadRequest());
        enviar("/api/v1/autodj/transition", "{\"fromTrackId\":0,\"toTrackId\":2}").andExpect(status().isBadRequest());
    }

    @Test
    void unaPistaInexistenteEnElMotorDevuelve404ConSuMensaje() throws Exception {
        when(autoDj.nextTracks(9, null)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(),
                "{\"error\":\"No existe la pista 9.\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        enviar("/api/v1/autodj/next", "{\"trackId\":9}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No existe la pista 9."));
    }

    @Test
    void siElMotorNoRespondeDevuelve502() throws Exception {
        when(autoDj.transition(1, 2)).thenThrow(new ResourceAccessException("Connection refused"));

        enviar("/api/v1/autodj/transition", "{\"fromTrackId\":1,\"toTrackId\":2}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("El motor de IA no esta disponible"));
    }

    @Test
    void registraLaValoracionYDevuelveLosPesosVigentes() throws Exception {
        when(autoDj.submitFeedback(1, 2, true)).thenReturn("{\"weights\":{\"tempo\":0.4},\"samples\":3,\"learned\":false}");

        enviar("/api/v1/autodj/feedback", "{\"fromTrackId\":1,\"toTrackId\":2,\"liked\":true}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.samples").value(3))
                .andExpect(jsonPath("$.learned").value(false));

        verify(autoDj).submitFeedback(1, 2, true);
    }

    @Test
    void laValoracionNegativaSeReenviaComoFalse() throws Exception {
        when(autoDj.submitFeedback(1, 2, false)).thenReturn("{}");

        enviar("/api/v1/autodj/feedback", "{\"fromTrackId\":1,\"toTrackId\":2,\"liked\":false}")
                .andExpect(status().isCreated());

        verify(autoDj).submitFeedback(1, 2, false);
    }

    @Test
    void unaValoracionInvalidaDevuelve400SinLlamarAlMotor() throws Exception {
        String[] casos = {
            "{}",
            "{\"fromTrackId\":1,\"toTrackId\":2}",                       // falta liked
            "{\"fromTrackId\":1,\"liked\":true}",                          // falta toTrackId
            "{\"fromTrackId\":0,\"toTrackId\":2,\"liked\":true}",          // id no positivo
            "{\"fromTrackId\":1,\"toTrackId\":2,\"liked\":\"si\"}",        // liked no es booleano
        };
        for (String caso : casos) {
            enviar("/api/v1/autodj/feedback", caso).andExpect(status().isBadRequest());
        }

        verify(autoDj, never()).submitFeedback(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void valorarUnaTransicionDeUnaPistaConsigoMismaDevuelve400() throws Exception {
        enviar("/api/v1/autodj/feedback", "{\"fromTrackId\":5,\"toTrackId\":5,\"liked\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No se puede valorar una transicion de una pista a si misma."));
    }

    @Test
    void unaPistaInexistenteEnLaValoracionDevuelveSu404() throws Exception {
        when(autoDj.submitFeedback(1, 99, true)).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(),
                "{\"error\":\"No existe la pista 99.\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        enviar("/api/v1/autodj/feedback", "{\"fromTrackId\":1,\"toTrackId\":99,\"liked\":true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No existe la pista 99."));
    }

    @Test
    void registraCuandoElDjPidioMezclarYDevuelveLoAprendido() throws Exception {
        when(autoDj.submitMixTiming(1, 2, 290.5, 250.0))
                .thenReturn("{\"samples\":3,\"advanceSeconds\":40.0,\"learned\":true}");

        enviar("/api/v1/autodj/timing",
                "{\"fromTrackId\":1,\"toTrackId\":2,\"plannedMixOutAt\":290.5,\"actualMixOutAt\":250}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.advanceSeconds").value(40.0))
                .andExpect(jsonPath("$.learned").value(true));

        verify(autoDj).submitMixTiming(1, 2, 290.5, 250.0);
    }

    @Test
    void unMomentoDeMezclaInvalidoDevuelve400SinLlamarAlMotor() throws Exception {
        String[] casos = {
            "{}",
            "{\"fromTrackId\":1,\"toTrackId\":2,\"plannedMixOutAt\":10}",                         // falta actualMixOutAt
            "{\"fromTrackId\":1,\"toTrackId\":2,\"plannedMixOutAt\":10,\"actualMixOutAt\":-1}",    // negativo
            "{\"fromTrackId\":0,\"toTrackId\":2,\"plannedMixOutAt\":10,\"actualMixOutAt\":5}",     // id no positivo
            "{\"fromTrackId\":1,\"toTrackId\":2,\"plannedMixOutAt\":\"x\",\"actualMixOutAt\":5}",  // no es numero
        };
        for (String caso : casos) {
            enviar("/api/v1/autodj/timing", caso).andExpect(status().isBadRequest());
        }

        verify(autoDj, never()).submitMixTiming(anyLong(), anyLong(),
                org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble());
    }

    @Test
    void unMomentoDeMezclaDeUnaPistaConsigoMismaDevuelve400() throws Exception {
        enviar("/api/v1/autodj/timing",
                "{\"fromTrackId\":5,\"toTrackId\":5,\"plannedMixOutAt\":10,\"actualMixOutAt\":5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("No se puede mezclar una pista consigo misma."));
    }

    @Test
    void consultaLosPesosVigentes() throws Exception {
        when(autoDj.weights()).thenReturn("{\"weights\":{\"tempo\":0.2,\"harmonic\":0.6,\"energy\":0.2},\"learned\":true,\"samples\":40}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/autodj/weights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learned").value(true))
                .andExpect(jsonPath("$.weights.harmonic").value(0.6))
                .andExpect(jsonPath("$.samples").value(40));
    }

    @Test
    void consultaElEstadoDelModeloDeEstilo() throws Exception {
        when(autoDj.styleModel()).thenReturn("{\"goodRecordings\":12,\"required\":20,\"ready\":false,\"enabled\":false}");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/autodj/style-model"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goodRecordings").value(12))
                .andExpect(jsonPath("$.required").value(20))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void enciendeElModeloDeEstilo() throws Exception {
        when(autoDj.setStyleModel(true)).thenReturn("{\"enabled\":true}");

        enviar("/api/v1/autodj/style-model", "{\"enabled\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void sinEnabledElModeloDeEstiloDevuelve400() throws Exception {
        enviar("/api/v1/autodj/style-model", "{}")
                .andExpect(status().isBadRequest());
    }
}
