package com.soundia.telemetry.stream;

import java.time.LocalDateTime;

import com.soundia.telemetry.model.DjSessionLog;

/**
 * Cuerpo del evento SSE "decision". Si el motor de IA no estuvo disponible, status es
 * "unavailable" y action es null.
 */
public record DecisionMessage(
        Long id,
        Integer controlId,
        Integer value,
        String status,
        String action,
        LocalDateTime createdIn
        ) {

    public static final String UNAVAILABLE = "unavailable";

    public static DecisionMessage from(DjSessionLog log, String status) {
        return new DecisionMessage(log.getId(), log.getControlId(), log.getValue(), status,
                log.getAction(), log.getCreatedIn());
    }
}
