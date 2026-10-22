package com.soundia.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.soundia.auth.AuthService.LoginOutcome;
import com.soundia.auth.AuthService.LoginOutcome.Status;

class AuthServiceTests {

    private static final String SECRET = "una-clave-de-prueba-de-al-menos-32-caracteres!";

    private MutableClock clock;
    private AuthService service;

    @BeforeEach
    void crear() {
        clock = new MutableClock();
        service = new AuthService(new AuthProperties(true, "dj", "mi-clave-secreta", SECRET,
                Duration.ofHours(8), Duration.ofSeconds(30), 3, Duration.ofMinutes(1)), clock);
    }

    @Test
    void conLasCredencialesDelDjEmiteUnTokenDeAccesoValido() {
        LoginOutcome resultado = service.login("dj", "mi-clave-secreta", "1.2.3.4");

        assertThat(resultado.status()).isEqualTo(Status.ACCEPTED);
        assertThat(service.tokens().validate(resultado.token().value(), TokenService.SCOPE_ACCESS)).contains("dj");
        assertThat(resultado.token().expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(8)));
    }

    @Test
    void conUnaContrasenaIncorrectaRechazaYNoEmiteToken() {
        LoginOutcome resultado = service.login("dj", "otra", "1.2.3.4");

        assertThat(resultado.status()).isEqualTo(Status.REJECTED);
        assertThat(resultado.token()).isNull();
    }

    @Test
    void conUnUsuarioIncorrectoRechazaIgualQueConUnaContrasenaIncorrecta() {
        LoginOutcome usuarioMal = service.login("intruso", "mi-clave-secreta", "1.2.3.4");
        LoginOutcome contrasenaMal = service.login("dj", "mala", "1.2.3.5");

        assertThat(usuarioMal).isEqualTo(contrasenaMal);
    }

    @Test
    void rechazaUsuarioYContrasenaVaciosONulos() {
        assertThat(service.login("", "", "1.2.3.4").status()).isEqualTo(Status.REJECTED);
        assertThat(service.login(null, null, "1.2.3.4").status()).isEqualTo(Status.REJECTED);
    }

    @Test
    void trasLosIntentosFallidosPermitidosBloqueaAunqueLaContrasenaSeaCorrecta() {
        for (int i = 0; i < 3; i++) {
            assertThat(service.login("dj", "mala", "1.2.3.4").status()).isEqualTo(Status.REJECTED);
        }

        LoginOutcome bloqueado = service.login("dj", "mi-clave-secreta", "1.2.3.4");

        assertThat(bloqueado.status()).isEqualTo(Status.BLOCKED);
        assertThat(bloqueado.retryAfterSeconds()).isPositive();
        assertThat(bloqueado.token()).isNull();
    }

    @Test
    void elBloqueoDeUnOrigenNoAfectaAOtroYSeLevantaConElTiempo() {
        for (int i = 0; i < 3; i++) {
            service.login("dj", "mala", "1.2.3.4");
        }

        assertThat(service.login("dj", "mi-clave-secreta", "5.6.7.8").status()).isEqualTo(Status.ACCEPTED);

        clock.advance(Duration.ofSeconds(61));
        assertThat(service.login("dj", "mi-clave-secreta", "1.2.3.4").status()).isEqualTo(Status.ACCEPTED);
    }

    @Test
    void unLoginCorrectoBorraLosFallosAnteriores() {
        service.login("dj", "mala", "1.2.3.4");
        service.login("dj", "mala", "1.2.3.4");
        service.login("dj", "mi-clave-secreta", "1.2.3.4");

        service.login("dj", "mala", "1.2.3.4");
        service.login("dj", "mala", "1.2.3.4");

        assertThat(service.login("dj", "mi-clave-secreta", "1.2.3.4").status()).isEqualTo(Status.ACCEPTED);
    }

    @Test
    void elTicketDelStreamSoloValeParaElStream() {
        TokenService.IssuedToken ticket = service.issueStreamTicket();

        assertThat(service.tokens().validate(ticket.value(), TokenService.SCOPE_STREAM)).contains("dj");
        assertThat(service.tokens().validate(ticket.value(), TokenService.SCOPE_ACCESS)).isEmpty();
        assertThat(ticket.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofSeconds(30)));
    }

    @Test
    void noArrancaConUnaConfiguracionInvalida() {
        AuthProperties sinClave = new AuthProperties(true, "dj", "clave", null,
                Duration.ofHours(8), Duration.ofSeconds(30), 3, Duration.ofMinutes(1));

        assertThatThrownBy(() -> new AuthService(sinClave, clock)).isInstanceOf(IllegalStateException.class);
    }
}
