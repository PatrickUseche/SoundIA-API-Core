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
}
