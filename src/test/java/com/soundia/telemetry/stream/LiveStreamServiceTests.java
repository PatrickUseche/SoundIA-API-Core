package com.soundia.telemetry.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class LiveStreamServiceTests {

    private LiveStreamService service;

    @BeforeEach
    void setUp() {
        service = new LiveStreamService(Duration.ofMinutes(1));
    }

    @Test
    void sinClientesConectadosPublicarNoFalla() {
        service.publish(StreamEventType.MIDI, "x");

        assertEquals(0, service.connectedClients());
    }

    @Test
    void registrarAgregaUnCliente() {
        SseEmitter emitter = service.register();

        assertEquals(1, service.connectedClients());
        emitter.complete();
    }
}
