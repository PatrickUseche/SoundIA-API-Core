package com.soundia.auth;

import java.time.Instant;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soundia.config.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Estado y acceso de la autenticacion. `status` existe siempre (el front pregunta si hay que pedir login); el
 * resto solo funciona con la autenticacion encendida. Las rutas /login y /status son publicas; /stream-ticket
 * exige el token de acceso (lo comprueba JwtAuthFilter).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthProperties properties;
    private final ObjectProvider<AuthService> service;

    public AuthController(AuthProperties properties, ObjectProvider<AuthService> service) {
        this.properties = properties;
        this.service = service;
    }

    public record StatusResponse(boolean enabled) {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 200) String username,
            @NotBlank @Size(max = 200) String password) {
    }

    public record TokenResponse(String token, Instant expiresAt) {
    }

    public record TicketResponse(String ticket, Instant expiresAt) {
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return new StatusResponse(properties.enabled());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthService auth = service.getIfAvailable();
        if (auth == null) {
            return notEnabled();
        }
        AuthService.LoginOutcome outcome = auth.login(request.username(), request.password(), http.getRemoteAddr());
        return switch (outcome.status()) {
            case ACCEPTED -> ResponseEntity.ok(new TokenResponse(outcome.token().value(), outcome.token().expiresAt()));
            // Mismo mensaje para usuario y contrasena incorrectos: no se dice cual de los dos fallo.
            case REJECTED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of("Usuario o contrasena incorrectos"));
            case BLOCKED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(outcome.retryAfterSeconds()))
                    .body(ErrorResponse.of("Demasiados intentos fallidos. Espera unos segundos e intenta de nuevo."));
        };
    }

    @PostMapping("/stream-ticket")
    public ResponseEntity<?> streamTicket() {
        AuthService auth = service.getIfAvailable();
        if (auth == null) {
            return notEnabled();
        }
        TokenService.IssuedToken ticket = auth.issueStreamTicket();
        return ResponseEntity.ok(new TicketResponse(ticket.value(), ticket.expiresAt()));
    }

    private static ResponseEntity<ErrorResponse> notEnabled() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of("La autenticacion no esta activada"));
    }
}
