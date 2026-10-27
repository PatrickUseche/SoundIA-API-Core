package com.soundia.telemetry.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.soundia.telemetry.model.DjSessionLog;

class StreamMessageFactoryTests {

    private final StreamMessageFactory factory = new StreamMessageFactory();

    @Test
    void creaElMensajeMidiConElControlYElValor() {
        StreamMessage mensaje = factory.create(StreamEventType.MIDI, new DjSessionLog(14, 127));

        assertEquals(new MidiMessage(14, 127), assertInstanceOf(MidiMessage.class, mensaje));
    }

    @Test
    void creaElMensajeDeDecisionConLosDatosDelRegistroYElEstado() {
        DjSessionLog log = new DjSessionLog(14, 127, "Corte de EQ en deck 14");
        log.setId(9L);

        DecisionMessage decision = assertInstanceOf(DecisionMessage.class,
                factory.create(StreamEventType.DECISION, log, "success"));

        assertEquals(9L, decision.id());
        assertEquals(14, decision.controlId());
        assertEquals("success", decision.status());
        assertEquals("Corte de EQ en deck 14", decision.action());
    }

    @Test
    void sinIaLaDecisionLlevaEstadoUnavailableYAccionNula() {
        DecisionMessage decision = assertInstanceOf(DecisionMessage.class,
                factory.create(StreamEventType.DECISION, new DjSessionLog(14, 127), DecisionMessage.UNAVAILABLE));

        assertEquals("unavailable", decision.status());
        assertNull(decision.action());
    }

    @Test
    void unTipoDesconocidoDaError() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> factory.create("otro", new DjSessionLog(1, 2)));

        assertTrue(error.getMessage().contains("otro"), error.getMessage());
    }
}
