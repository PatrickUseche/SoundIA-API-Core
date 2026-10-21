package com.soundia.telemetry.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.soundia.telemetry.model.DjSessionLog;

class LiveStreamPublisherTests {

    private final LiveStreamService liveStream = mock(LiveStreamService.class);
    private final LiveStreamPublisher publisher = new LiveStreamPublisher(liveStream);

    @Test
    void publicaLaDecisionConLosDatosDelRegistroGuardado() {
        DjSessionLog log = new DjSessionLog(14, 127, "Corte de EQ en deck 14");
        log.setId(9L);

        publisher.onDecision(new MidiDecisionEvent(log, "success"));

        ArgumentCaptor<Object> mensaje = ArgumentCaptor.forClass(Object.class);
        verify(liveStream).publish(org.mockito.ArgumentMatchers.eq(StreamEventType.DECISION), mensaje.capture());
        DecisionMessage decision = (DecisionMessage) mensaje.getValue();
        assertEquals(9L, decision.id());
        assertEquals(14, decision.controlId());
        assertEquals("success", decision.status());
        assertEquals("Corte de EQ en deck 14", decision.action());
    }

    @Test
    void sinIaPublicaEstadoUnavailableYAccionNula() {
        publisher.onDecision(new MidiDecisionEvent(new DjSessionLog(14, 127), DecisionMessage.UNAVAILABLE));

        ArgumentCaptor<Object> mensaje = ArgumentCaptor.forClass(Object.class);
        verify(liveStream).publish(org.mockito.ArgumentMatchers.eq(StreamEventType.DECISION), mensaje.capture());
        DecisionMessage decision = (DecisionMessage) mensaje.getValue();
        assertEquals("unavailable", decision.status());
        assertNull(decision.action());
    }
}
