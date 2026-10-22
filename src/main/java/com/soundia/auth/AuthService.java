package com.soundia.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Inicio de sesion del unico usuario DJ. La contrasena configurada se guarda en memoria solo como hash BCrypt y
 * las comparaciones no revelan (por tiempo) si fallo el usuario o la contrasena.
 */
public class AuthService {

    private final AuthProperties properties;
    private final TokenService tokens;
    private final LoginThrottle throttle;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String passwordHash;
    private final byte[] usernameBytes;

    public AuthService(AuthProperties properties, Clock clock) {
        properties.validate();
        this.properties = properties;
        this.tokens = new TokenService(properties.jwtSecret(), clock);
        this.throttle = new LoginThrottle(properties.maxLoginAttempts(), properties.loginWindow(), clock);
        this.passwordHash = encoder.encode(properties.password());
        this.usernameBytes = properties.username().getBytes(StandardCharsets.UTF_8);
    }

    public TokenService tokens() {
        return tokens;
    }

    /**
     * Comprueba las credenciales de `source` (el origen de la conexion) y, si son las del DJ, emite un token de
     * acceso. Siempre hace la comparacion de la contrasena, aunque el usuario no coincida.
     */
    public LoginOutcome login(String username, String password, String source) {
        long wait = throttle.secondsUntilAllowed(source);
        if (wait > 0) {
            return LoginOutcome.blocked(wait);
        }
        boolean sameUser = MessageDigest.isEqual(
                usernameBytes, (username == null ? "" : username).getBytes(StandardCharsets.UTF_8));
        boolean samePassword = encoder.matches(password == null ? "" : password, passwordHash);
        if (!(sameUser && samePassword)) {
            throttle.recordFailure(source);
            return LoginOutcome.rejected();
        }
        throttle.recordSuccess(source);
        return LoginOutcome.accepted(tokens.issue(properties.username(), TokenService.SCOPE_ACCESS, properties.tokenTtl()));
    }

    /** Ticket de vida corta para abrir el stream en vivo. */
    public TokenService.IssuedToken issueStreamTicket() {
        return tokens.issue(properties.username(), TokenService.SCOPE_STREAM, properties.streamTicketTtl());
    }

    public record LoginOutcome(Status status, TokenService.IssuedToken token, long retryAfterSeconds) {

        public enum Status { ACCEPTED, REJECTED, BLOCKED }

        static LoginOutcome accepted(TokenService.IssuedToken token) {
            return new LoginOutcome(Status.ACCEPTED, token, 0);
        }

        static LoginOutcome rejected() {
            return new LoginOutcome(Status.REJECTED, null, 0);
        }

        static LoginOutcome blocked(long retryAfterSeconds) {
            return new LoginOutcome(Status.BLOCKED, null, retryAfterSeconds);
        }
    }
}
