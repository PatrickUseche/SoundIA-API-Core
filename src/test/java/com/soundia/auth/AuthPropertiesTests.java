package com.soundia.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class AuthPropertiesTests {

    private static final String SECRET = "una-clave-de-prueba-de-al-menos-32-caracteres!";

    private static AuthProperties con(boolean enabled, String usuario, String contrasena, String secreto) {
        return new AuthProperties(enabled, usuario, contrasena, secreto,
                Duration.ofHours(8), Duration.ofSeconds(30), 5, Duration.ofMinutes(1));
    }

    @Test
    void apagadaNoExigeNingunaConfiguracion() {
        assertThatCode(() -> con(false, null, null, null).validate()).doesNotThrowAnyException();
    }

    @Test
    void encendidaYBienConfiguradaEsValida() {
        assertThatCode(() -> con(true, "dj", "clave", SECRET).validate()).doesNotThrowAnyException();
    }

    @Test
    void encendidaSinUsuarioFallaDiciendoQueVariableFalta() {
        assertThatThrownBy(() -> con(true, " ", "clave", SECRET).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SOUNDIA_AUTH_USERNAME");
    }

    @Test
    void encendidaSinContrasenaFalla() {
        assertThatThrownBy(() -> con(true, "dj", null, SECRET).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SOUNDIA_AUTH_PASSWORD");
    }

    @Test
    void encendidaSinClaveDeFirmaFalla() {
        assertThatThrownBy(() -> con(true, "dj", "clave", null).validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SOUNDIA_AUTH_JWT_SECRET");
    }

    @Test
    void unaClaveDeFirmaCortaFalla() {
        assertThatThrownBy(() -> con(true, "dj", "clave", "corta").validate())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32");
    }

    @Test
    void unaClaveDeExactamenteElMinimoEsValida() {
        assertThatCode(() -> con(true, "dj", "clave", "x".repeat(AuthProperties.MIN_SECRET_BYTES)).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void lasDuracionesYElLimiteDebenSerPositivos() {
        assertThatThrownBy(() -> new AuthProperties(true, "dj", "clave", SECRET,
                Duration.ZERO, Duration.ofSeconds(30), 5, Duration.ofMinutes(1)).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AuthProperties(true, "dj", "clave", SECRET,
                Duration.ofHours(1), Duration.ofSeconds(-1), 5, Duration.ofMinutes(1)).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AuthProperties(true, "dj", "clave", SECRET,
                Duration.ofHours(1), Duration.ofSeconds(30), 0, Duration.ofMinutes(1)).validate())
                .isInstanceOf(IllegalStateException.class);
    }
}
