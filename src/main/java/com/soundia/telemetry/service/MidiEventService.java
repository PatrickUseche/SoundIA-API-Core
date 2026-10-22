package com.soundia.telemetry.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.soundia.telemetry.dto.AiDecisionDto;
import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.repository.DjSessionLogRepository;
import com.soundia.telemetry.stream.DecisionMessage;
import com.soundia.telemetry.stream.MidiDecisionEvent;

/**
 * Flujo unico de un evento MIDI: se consulta al motor de IA y se guarda UN solo registro con la
 * decision. Lo usan tanto /event (asincrono, via observador) como /ai/predict (sincrono).
 */
@Service
public class MidiEventService {

    private final IaEngineClient iaEngine;
    private final DjSessionLogRepository repository;
    private final ApplicationEventPublisher events;

    public MidiEventService(IaEngineClient iaEngine, DjSessionLogRepository repository,
                            ApplicationEventPublisher events) {
        this.iaEngine = iaEngine;
        this.repository = repository;
        this.events = events;
    }

    /**
     * Calcula la decision y la guarda junto al evento. Si el motor falla no se guarda nada, asi
     * un reintento no duplica registros.
     */
    public AiDecisionDto decideAndStore(Integer controlId, Integer value) {
        AiDecisionDto decision = iaEngine.calculateMix(controlId, value);
        DjSessionLog saved = repository.save(new DjSessionLog(controlId, value, decision.action()));
        events.publishEvent(new MidiDecisionEvent(saved, decision.status()));
        return decision;
    }

    /** Guarda el evento sin decision cuando el motor de IA no esta disponible. */
    public void storeWithoutDecision(Integer controlId, Integer value) {
        DjSessionLog saved = repository.save(new DjSessionLog(controlId, value));
        events.publishEvent(new MidiDecisionEvent(saved, DecisionMessage.UNAVAILABLE));
    }
}
