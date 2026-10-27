package com.soundia.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class EngineStatusTests {

    private static final Instant AHORA = Instant.parse("2026-10-27T15:00:00Z");

    private final EngineStatus status = EngineStatus.forTesting(Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void siempreDevuelveLaMismaInstancia() {
        assertSame(EngineStatus.getInstance(), EngineStatus.getInstance());
    }

    @Test
    void lasInstanciasDeLaAplicacionVienenDeUnSoloLugar() throws Exception {
        int hilos = 16;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<java.util.concurrent.Future<EngineStatus>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            futuros.add(pool.submit(() -> {
                salida.await();
                return EngineStatus.getInstance();
            }));
        }
        salida.countDown();

        for (var futuro : futuros) {
            assertSame(EngineStatus.getInstance(), futuro.get(5, TimeUnit.SECONDS));
        }
        pool.shutdownNow();
    }

    @Test
    void alInicioNoSeSabeNada() {
        EngineStatus.Snapshot foto = status.snapshot();

        assertEquals(EngineStatus.State.UNKNOWN, foto.state());
        assertEquals(0, foto.consecutiveFailures());
        assertNull(foto.lastSuccessAt());
        assertNull(foto.lastFailureAt());
        assertNull(foto.lastError());
    }

    @Test
    void unaLlamadaBuenaDejaElMotorArriba() {
        status.recordSuccess();

        EngineStatus.Snapshot foto = status.snapshot();
        assertEquals(EngineStatus.State.UP, foto.state());
        assertEquals(AHORA, foto.lastSuccessAt());
    }

    @Test
    void losFallosSeCuentanSeguidosYGuardanElMotivo() {
        status.recordFailure("Connection refused");
        status.recordFailure("Read timed out");

        EngineStatus.Snapshot foto = status.snapshot();
        assertEquals(EngineStatus.State.DOWN, foto.state());
        assertEquals(2, foto.consecutiveFailures());
        assertEquals("Read timed out", foto.lastError());
        assertEquals(AHORA, foto.lastFailureAt());
    }

    @Test
    void unaLlamadaBuenaTrasFallarReiniciaLaCuentaPeroConservaElUltimoError() {
        status.recordFailure("Connection refused");

        status.recordSuccess();

        EngineStatus.Snapshot foto = status.snapshot();
        assertEquals(EngineStatus.State.UP, foto.state());
        assertEquals(0, foto.consecutiveFailures());
        assertEquals("Connection refused", foto.lastError());
    }

    @Test
    void resetVuelveAlEstadoInicial() {
        status.recordFailure("x");

        status.reset();

        assertEquals(EngineStatus.State.UNKNOWN, status.snapshot().state());
        assertNull(status.snapshot().lastError());
    }

    @Test
    void variosHilosPuedenRegistrarFallosSinPerderNinguno() throws Exception {
        int hilos = 8;
        int porHilo = 500;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                for (int j = 0; j < porHilo; j++) {
                    status.recordFailure("x");
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(hilos * porHilo, status.snapshot().consecutiveFailures());
    }

    @Test
    void trackDevuelveElResultadoYDejaElMotorArriba() {
        String resultado = status.track(() -> "ok");

        assertEquals("ok", resultado);
        assertEquals(EngineStatus.State.UP, status.snapshot().state());
    }

    @Test
    void trackMarcaCaidoSiNoSeLlegaAlMotorYRelanzaElError() {
        ResourceAccessException error = assertThrows(ResourceAccessException.class,
                () -> status.track(() -> { throw new ResourceAccessException("Connection refused"); }));

        assertEquals("Connection refused", error.getMessage());
        assertEquals(EngineStatus.State.DOWN, status.snapshot().state());
        assertEquals(1, status.snapshot().consecutiveFailures());
    }

    @Test
    void trackMarcaCaidoCuandoElMotorResponde5xx() {
        assertThrows(HttpServerErrorException.class, () -> status.track(() -> {
            throw HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", HttpHeaders.EMPTY, new byte[0], null);
        }));

        assertEquals(EngineStatus.State.DOWN, status.snapshot().state());
    }

    @Test
    void unErrorDePeticionDel4xxNoCuentaComoMotorCaido() {
        status.recordFailure("antes");

        assertThrows(HttpClientErrorException.class, () -> status.track(() -> {
            throw HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
        }));

        // El motor contesto, asi que esta arriba aunque la pista no exista.
        assertEquals(EngineStatus.State.UP, status.snapshot().state());
        assertEquals(0, status.snapshot().consecutiveFailures());
    }
}
