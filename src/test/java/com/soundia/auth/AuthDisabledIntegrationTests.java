package com.soundia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Por defecto (sin soundia.auth.enabled) el API funciona como siempre, sin login. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthDisabledIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void elApiRespondeSinTokenYElEstadoDiceQueNoHayLogin() throws Exception {
        mockMvc.perform(get("/api/v1/midi/logs")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void noSeCreaNingunaPiezaDeAutenticacion() {
        assertThat(context.getBeansOfType(AuthService.class)).isEmpty();
        assertThat(context.getBeansOfType(JwtAuthFilter.class)).isEmpty();
    }

    @Test
    void encendidaPeroMalConfiguradaLaAplicacionNoArranca() {
        new ApplicationContextRunner()
                .withBean(Clock.class, Clock::systemUTC)
                .withUserConfiguration(AuthConfig.class)
                .withPropertyValues("soundia.auth.enabled=true", "soundia.auth.username=dj")
                .run(arranque -> {
                    assertThat(arranque).hasFailed();
                    assertThat(arranque.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void encendidaYBienConfiguradaSeCreanElServicioYElFiltro() {
        new ApplicationContextRunner()
                .withBean(Clock.class, Clock::systemUTC)
                .withUserConfiguration(AuthConfig.class)
                .withPropertyValues(
                        "soundia.auth.enabled=true", "soundia.auth.username=dj", "soundia.auth.password=clave",
                        "soundia.auth.jwt-secret=una-clave-de-prueba-de-al-menos-32-caracteres!")
                .run(arranque -> {
                    assertThat(arranque).hasNotFailed();
                    assertThat(arranque).hasSingleBean(AuthService.class);
                });
    }
}
