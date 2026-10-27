package com.soundia.telemetry.stream;

import org.springframework.stereotype.Component;

import com.soundia.telemetry.model.DjSessionLog;

/**
 * Patron Factory Method: crea el mensaje SSE que corresponde a cada tipo de evento. Quien publica pide el mensaje
 * por el tipo (`StreamEventType`) y no conoce las clases concretas; un tipo nuevo se agrega aqui y en nada mas.
 */
@Component
public class StreamMessageFactory {

    /**
     * El mensaje del tipo indicado, armado con los datos del registro.
     *
     * @param eventType uno de los valores de {@link StreamEventType}
     * @param status    estado de la decision de la IA; solo lo usa {@link StreamEventType#DECISION}
     * @throws IllegalArgumentException si el tipo no existe
     */
    public StreamMessage create(String eventType, DjSessionLog log, String status) {
        return switch (eventType) {
            case StreamEventType.MIDI -> new MidiMessage(log.getControlId(), log.getValue());
            case StreamEventType.DECISION -> DecisionMessage.from(log, status);
            default -> throw new IllegalArgumentException("Tipo de evento SSE desconocido: " + eventType);
        };
    }

    /** El mensaje de un evento que no lleva decision de la IA. */
    public StreamMessage create(String eventType, DjSessionLog log) {
        return create(eventType, log, null);
    }
}
