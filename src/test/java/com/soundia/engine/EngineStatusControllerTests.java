package com.soundia.engine;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EngineStatusController.class)
class EngineStatusControllerTests {

    @Autowired
    private MockMvc mockMvc;

    // El estado es global (Singleton): cada prueba parte y termina de cero.
    @BeforeEach
    @AfterEach
    void reiniciarEstado() {
        EngineStatus.getInstance().reset();
    }

    @Test
    void sinLlamadasAlMotorElEstadoEsDesconocido() throws Exception {
        mockMvc.perform(get("/api/v1/engine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UNKNOWN"))
                .andExpect(jsonPath("$.consecutiveFailures").value(0));
    }

    @Test
    void trasUnFalloInformaQueElMotorEstaCaido() throws Exception {
        EngineStatus.getInstance().recordFailure("Connection refused");

        mockMvc.perform(get("/api/v1/engine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DOWN"))
                .andExpect(jsonPath("$.consecutiveFailures").value(1))
                .andExpect(jsonPath("$.lastError").value("Connection refused"));
    }
}
