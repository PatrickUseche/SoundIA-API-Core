package com.soundia.telemetry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

import com.soundia.telemetry.dto.AiDecisionDto;
import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.repository.DjSessionLogRepository;

class MidiEventServiceTests {

    private IaEngineClient iaEngine;
    private DjSessionLogRepository repository;
    private MidiEventService service;

    @BeforeEach
    void setUp() {
        iaEngine = mock(IaEngineClient.class);
        repository = mock(DjSessionLogRepository.class);
        service = new MidiEventService(iaEngine, repository);
    }

    @Test
    void guardaUnSoloRegistroConLaAccionDeLaIa() {
        AiDecisionDto decision = new AiDecisionDto("success", "Corte de EQ en deck 14");
        when(iaEngine.calculateMix(14, 127)).thenReturn(decision);

        AiDecisionDto resultado = service.decideAndStore(14, 127);

        assertSame(decision, resultado);
        ArgumentCaptor<DjSessionLog> guardado = ArgumentCaptor.forClass(DjSessionLog.class);
        verify(repository).save(guardado.capture());
        assertEquals(14, guardado.getValue().getControlId());
        assertEquals(127, guardado.getValue().getValue());
        assertEquals("Corte de EQ en deck 14", guardado.getValue().getAction());
    }

    @Test
    void siElMotorFallaNoSeGuardaNadaYElErrorSePropaga() {
        when(iaEngine.calculateMix(14, 127)).thenThrow(new ResourceAccessException("Django caido"));

        assertThrows(ResourceAccessException.class, () -> service.decideAndStore(14, 127));

        verify(repository, never()).save(any());
    }

    @Test
    void sinDecisionGuardaElEventoConAccionNula() {
        service.storeWithoutDecision(14, 127);

        ArgumentCaptor<DjSessionLog> guardado = ArgumentCaptor.forClass(DjSessionLog.class);
        verify(repository).save(guardado.capture());
        assertEquals(14, guardado.getValue().getControlId());
        assertNull(guardado.getValue().getAction());
    }
}
