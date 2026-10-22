package com.soundia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Comprueba como se llaman, de verdad, las variables de entorno que configuran la aplicacion (lo que se
 * escribe en docker-compose.yml), simulando el entorno del sistema con un mapa.
 */
class EnvironmentVariablesBindingTests {

    private IaEngineProperties enlazar(Map<String, Object> entorno) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("sistema", entorno));
        return new Binder(ConfigurationPropertySources.get(env))
                .bind("soundia.ia-engine", IaEngineProperties.class)
                .orElseGet(() -> new IaEngineProperties("http://localhost:8000", Duration.ofSeconds(2),
                        Duration.ofSeconds(5), Duration.ofSeconds(120)));
    }

    @Test
    void laUrlDelMotorSeConfiguraConSOUNDIA_IA_ENGINE_BASE_URL() {
        assertEquals("http://ia-engine:8000",
                enlazar(Map.of("SOUNDIA_IA_ENGINE_BASE_URL", "http://ia-engine:8000")).baseUrl());
    }

    @Test
    void elTimeoutDeAnalisisSeConfiguraConSOUNDIA_IA_ENGINE_ANALYSIS_TIMEOUT() {
        assertEquals(Duration.ofSeconds(45),
                enlazar(Map.of("SOUNDIA_IA_ENGINE_ANALYSIS_TIMEOUT", "45s")).analysisTimeout());
    }

    @Test
    void losTimeoutsDeConexionYLecturaSeConfiguranPorEntorno() {
        IaEngineProperties props = enlazar(Map.of(
                "SOUNDIA_IA_ENGINE_CONNECT_TIMEOUT", "3s", "SOUNDIA_IA_ENGINE_READ_TIMEOUT", "9s"));

        assertEquals(Duration.ofSeconds(3), props.connectTimeout());
        assertEquals(Duration.ofSeconds(9), props.readTimeout());
    }

    /** Trampa conocida: quitar los guiones bajos NO funciona; el nombre correcto lleva los guiones bajos. */
    @Test
    void lasVariablesSinGuionesBajosNoSeReconocen() {
        assertEquals("http://localhost:8000",
                enlazar(Map.of("SOUNDIA_IAENGINE_BASEURL", "http://ia-engine:8000")).baseUrl());
    }

    @Test
    void sinVariablesSeUsanLosValoresPorDefecto() {
        IaEngineProperties props = enlazar(Map.of());

        assertEquals("http://localhost:8000", props.baseUrl());
        assertEquals(Duration.ofSeconds(120), props.analysisTimeout());
    }

    /** Las propiedades que se leen con @Value se resuelven con guiones bajos (kebab-case -> SNAKE_CASE). */
    private String propiedad(String nombre, Map<String, Object> entorno) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("sistema", entorno));
        return env.getProperty(nombre);
    }

    @Test
    void losOrigenesCorsSeConfiguranConSOUNDIA_CORS_ALLOWED_ORIGINS() {
        assertEquals("http://localhost:4200,https://dj.soundia.app",
                propiedad("soundia.cors.allowed-origins",
                        Map.of("SOUNDIA_CORS_ALLOWED_ORIGINS", "http://localhost:4200,https://dj.soundia.app")));
    }

    @Test
    void laConexionSseSeConfiguraConSOUNDIA_STREAM_TIMEOUT_Y_HEARTBEAT() {
        assertEquals("10m", propiedad("soundia.stream.timeout", Map.of("SOUNDIA_STREAM_TIMEOUT", "10m")));
        assertEquals("5s", propiedad("soundia.stream.heartbeat", Map.of("SOUNDIA_STREAM_HEARTBEAT", "5s")));
    }

    @Test
    void laBaseDeDatosSeConfiguraConDB_URL_DB_USERNAME_Y_DB_PASSWORD() {
        assertEquals("secreto", propiedad("DB_PASSWORD", Map.of("DB_PASSWORD", "secreto")));
        assertEquals("jdbc:postgresql://db:5432/soundia_db",
                propiedad("DB_URL", Map.of("DB_URL", "jdbc:postgresql://db:5432/soundia_db")));
    }
}
