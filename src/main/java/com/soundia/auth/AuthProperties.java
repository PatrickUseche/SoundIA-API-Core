package com.soundia.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Autenticacion del API con un unico usuario DJ. Se configura con soundia.auth.* o con las variables de entorno
 * equivalentes (SOUNDIA_AUTH_ENABLED, SOUNDIA_AUTH_USERNAME, SOUNDIA_AUTH_PASSWORD, SOUNDIA_AUTH_JWT_SECRET...).
 *
 * Viene APAGADA por defecto para no romper el uso local sin login; en un despliegue hay que encenderla. Con ella
 * encendida, el usuario, la contrasena y la clave de firma son obligatorios: sin ellos la aplicacion no arranca.
 */
@ConfigurationProperties(prefix = "soundia.auth")
public record AuthProperties(
        @DefaultValue("false") boolean enabled,
        String username,
        String password,
        // Clave con la que se firman los tokens (HS256): al menos MIN_SECRET_BYTES bytes, aleatoria y secreta.
        String jwtSecret,
        // Vida del token de acceso: pasado este tiempo hay que volver a iniciar sesion.
        @DefaultValue("8h") Duration tokenTtl,
        // Vida del "ticket" para abrir el stream en vivo (EventSource no puede enviar cabeceras): muy corta.
        @DefaultValue("30s") Duration streamTicketTtl,
        // Intentos fallidos de login por direccion dentro de la ventana, antes de bloquear con 429.
        @DefaultValue("5") int maxLoginAttempts,
        @DefaultValue("1m") Duration loginWindow) {

    public static final int MIN_SECRET_BYTES = 32;

    /** Falla con un mensaje claro si la autenticacion esta encendida pero mal configurada. */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (isBlank(username)) {
            throw new IllegalStateException("soundia.auth.enabled=true pero falta SOUNDIA_AUTH_USERNAME.");
        }
        if (isBlank(password)) {
            throw new IllegalStateException("soundia.auth.enabled=true pero falta SOUNDIA_AUTH_PASSWORD.");
        }
        if (isBlank(jwtSecret) || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "soundia.auth.enabled=true pero SOUNDIA_AUTH_JWT_SECRET falta o tiene menos de "
                            + MIN_SECRET_BYTES + " caracteres.");
        }
        if (tokenTtl.isZero() || tokenTtl.isNegative() || streamTicketTtl.isZero() || streamTicketTtl.isNegative()) {
            throw new IllegalStateException("Las duraciones de los tokens deben ser positivas.");
        }
        if (maxLoginAttempts < 1 || loginWindow.isZero() || loginWindow.isNegative()) {
            throw new IllegalStateException("El limite de intentos de login debe ser positivo.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
