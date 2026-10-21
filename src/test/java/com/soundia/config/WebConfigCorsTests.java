package com.soundia.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.soundia.telemetry.controller.MidiTelemetryController;
import com.soundia.telemetry.pattern.DjControllerReader;
import com.soundia.telemetry.repository.DjSessionLogRepository;
import com.soundia.telemetry.service.MidiEventService;

class WebConfigCorsTests {

    abstract static class Base {
        @Autowired
        MockMvc mockMvc;

        ResultActions preflight(String origin) throws Exception {
            return mockMvc.perform(options("/api/v1/midi/logs")
                    .header("Origin", origin)
                    .header("Access-Control-Request-Method", "GET"));
        }
    }

    @Nested
    @WebMvcTest(MidiTelemetryController.class)
    @Import(WebConfig.class)
    class ConOrigenPorDefecto extends Base {
        @MockitoBean DjControllerReader reader;
        @MockitoBean DjSessionLogRepository repository;
        @MockitoBean MidiEventService service;

        @Test
        void permiteElFrontendLocalDeAngular() throws Exception {
            preflight("http://localhost:4200")
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        }

        @Test
        void rechazaOrigenesNoConfigurados() throws Exception {
            preflight("http://sitio-malicioso.com").andExpect(status().isForbidden());
        }
    }

    @Nested
    @WebMvcTest(MidiTelemetryController.class)
    @Import(WebConfig.class)
    @TestPropertySource(properties = "soundia.cors.allowed-origins=https://dj.soundia.app, http://localhost:4300")
    class ConOrigenesConfigurados extends Base {
        @MockitoBean DjControllerReader reader;
        @MockitoBean DjSessionLogRepository repository;
        @MockitoBean MidiEventService service;

        @Test
        void permiteCadaOrigenDeLaLista() throws Exception {
            preflight("https://dj.soundia.app").andExpect(status().isOk());
            preflight("http://localhost:4300").andExpect(status().isOk());
        }

        @Test
        void yaNoPermiteElOrigenPorDefecto() throws Exception {
            preflight("http://localhost:4200").andExpect(status().isForbidden());
        }
    }
}
