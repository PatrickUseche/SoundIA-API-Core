package com.soundia.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(DomainExceptionHandlerTests.LanzaErrores.class)
@Import(DomainExceptionHandlerTests.LanzaErrores.class)
class DomainExceptionHandlerTests {

    /** Controlador solo de pruebas: lanza cada excepcion de dominio. */
    @RestController
    static class LanzaErrores {
        @GetMapping("/test/no-existe")
        String noExiste() {
            throw new ResourceNotFoundException("No existe la sesion 7.");
        }

        @GetMapping("/test/conflicto")
        String conflicto() {
            throw new ConflictException("La sesion ya termino.");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void recursoInexistenteDevuelve404ConSuMensaje() throws Exception {
        mockMvc.perform(get("/test/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("No existe la sesion 7."));
    }

    @Test
    void conflictoDevuelve409ConSuMensaje() throws Exception {
        mockMvc.perform(get("/test/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("La sesion ya termino."));
    }
}
