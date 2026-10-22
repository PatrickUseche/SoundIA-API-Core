package com.soundia.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginThrottleTests {

    private MutableClock clock;
    private LoginThrottle throttle;

    @BeforeEach
    void crear() {
        clock = new MutableClock();
        throttle = new LoginThrottle(3, Duration.ofMinutes(1), clock);
    }

    private void fallar(String origen, int veces) {
        for (int i = 0; i < veces; i++) {
            throttle.recordFailure(origen);
        }
    }

    @Test
    void sinFallosNoBloquea() {
        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isZero();
    }

    @Test
    void conMenosFallosDelLimiteNoBloquea() {
        fallar("1.2.3.4", 2);

        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isZero();
    }

    @Test
    void alAlcanzarElLimiteBloqueaYDiceCuantoEsperar() {
        fallar("1.2.3.4", 3);

        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isBetween(1L, 61L);
    }

    @Test
    void elBloqueoSeLevantaCuandoElFalloMasAntiguoSaleDeLaVentana() {
        fallar("1.2.3.4", 3);

        clock.advance(Duration.ofSeconds(59));
        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isPositive();

        clock.advance(Duration.ofSeconds(2));
        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isZero();
    }

    @Test
    void unLoginCorrectoBorraLosFallos() {
        fallar("1.2.3.4", 3);

        throttle.recordSuccess("1.2.3.4");

        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isZero();
    }

    @Test
    void cadaOrigenTieneSuPropioContador() {
        fallar("1.2.3.4", 3);

        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isPositive();
        assertThat(throttle.secondsUntilAllowed("5.6.7.8")).isZero();
    }

    @Test
    void losFallosViejosNoSeAcumulanConLosNuevos() {
        fallar("1.2.3.4", 2);
        clock.advance(Duration.ofSeconds(61));

        fallar("1.2.3.4", 2);

        assertThat(throttle.secondsUntilAllowed("1.2.3.4")).isZero();
    }
}
