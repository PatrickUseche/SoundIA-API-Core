package com.soundia.telemetry.pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import com.soundia.telemetry.dto.AiDecisionDto;
import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.service.MidiEventService;

/**
 * Observador que procesa cada evento MIDI: consulta al motor de IA y guarda el resultado. Reemplaza
 * a los observadores separados de BD e IA, que escribian el mismo registro en paralelo.
 */
@Component
public class MidiProcessingObserver implements MidiObserver {

    private static final Logger log = LoggerFactory.getLogger(MidiProcessingObserver.class);

    private final MidiEventService service;

    public MidiProcessingObserver(MidiEventService service) {
        this.service = service;
    }

    @Async
    @Retryable(
            retryFor = RestClientException.class, // Solo reintenta si es error de red.
            maxAttempts = 3, // Maximo 3 intentos.
            backoff = @Backoff(delayExpression = "${soundia.ia-engine.retry-delay-ms:2000}")
    )
    @Override
    public void onMidiEvent(DjSessionLog logEvent) {
        AiDecisionDto decision = service.decideAndStore(logEvent.getControlId(), logEvent.getValue());
        log.info("[Motor IA] Transicion calculada: {}", decision.action());
    }

    @Recover
    public void recover(RestClientException e, DjSessionLog logEvent) {
        log.error("[Motor IA] Abortado tras 3 reintentos. Django no responde para el control {}: {}",
                logEvent.getControlId(), e.getMessage());
        // El evento no se pierde: queda registrado sin decision de la IA.
        service.storeWithoutDecision(logEvent.getControlId(), logEvent.getValue());
    }
}
