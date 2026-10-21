package com.soundia.telemetry.service;

import org.springframework.stereotype.Service;

import com.soundia.telemetry.dto.AiDecisionDto;
import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.repository.DjSessionLogRepository;

/**
 * Flujo unico de un evento MIDI: se consulta al motor de IA y se guarda UN solo registro con la
 * decision. Lo usan tanto /event (asincrono, via observador) como /ai/predict (sincrono).
 */
@Service
public class MidiEventService {

    private final IaEngineClient iaEngine;
    private final DjSessionLogRepository repository;

    public MidiEventService(IaEngineClient iaEngine, DjSessionLogRepository repository) {
        this.iaEngine = iaEngine;
        this.repository = repository;
    }

    /**
     * Calcula la decision y la guarda junto al evento. Si el motor falla no se guarda nada, asi
     * un reintento no duplica registros.
     */
    public AiDecisionDto decideAndStore(Integer controlId, Integer value) {
        AiDecisionDto decision = iaEngine.calculateMix(controlId, value);
        repository.save(new DjSessionLog(controlId, value, decision.action()));
        return decision;
    }

    /** Guarda el evento sin decision cuando el motor de IA no esta disponible. */
    public void storeWithoutDecision(Integer controlId, Integer value) {
        repository.save(new DjSessionLog(controlId, value));
    }
}
