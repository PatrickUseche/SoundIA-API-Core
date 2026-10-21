package com.soundia.telemetry.pattern;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.ResourceAccessException;

import com.soundia.telemetry.dto.AiDecisionDto;
import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.service.MidiEventService;

/** Verifica los reintentos y la recuperacion del observador con el proxy real de spring-retry. */
@SpringJUnitConfig(MidiProcessingObserverTests.Config.class)
@TestPropertySource(properties = "soundia.ia-engine.retry-delay-ms=1")
class MidiProcessingObserverTests {

    @Configuration
    @EnableRetry
    static class Config {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        MidiEventService midiEventService() {
            return mock(MidiEventService.class);
        }

        @Bean
        MidiProcessingObserver observer(MidiEventService service) {
            return new MidiProcessingObserver(service);
        }
    }

    @Autowired
    private MidiObserver observer;

    @Autowired
    private MidiEventService service;

    @BeforeEach
    void limpiarMock() {
        org.mockito.Mockito.reset(service);
    }

    private static DjSessionLog evento() {
        return new DjSessionLog(14, 127);
    }

    @Test
    void procesaElEventoConElServicio() {
        when(service.decideAndStore(14, 127)).thenReturn(new AiDecisionDto("success", "x"));

        observer.onMidiEvent(evento());

        verify(service, times(1)).decideAndStore(14, 127);
        verify(service, never()).storeWithoutDecision(14, 127);
    }

    @Test
    void reintentaSiDjangoFallaYTerminaConExito() {
        when(service.decideAndStore(14, 127))
                .thenThrow(new ResourceAccessException("caido"))
                .thenThrow(new ResourceAccessException("caido"))
                .thenReturn(new AiDecisionDto("success", "x"));

        observer.onMidiEvent(evento());

        verify(service, times(3)).decideAndStore(14, 127);
        verify(service, never()).storeWithoutDecision(14, 127);
    }

    @Test
    void trasTresFallosGuardaElEventoSinDecision() {
        when(service.decideAndStore(14, 127)).thenThrow(new ResourceAccessException("caido"));

        observer.onMidiEvent(evento());

        verify(service, times(3)).decideAndStore(14, 127);
        verify(service, times(1)).storeWithoutDecision(14, 127);
    }
}
