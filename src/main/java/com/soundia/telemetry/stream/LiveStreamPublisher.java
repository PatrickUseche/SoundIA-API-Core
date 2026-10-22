package com.soundia.telemetry.stream;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Traduce los eventos internos de la aplicacion a mensajes SSE para el navegador. */
@Component
public class LiveStreamPublisher {

    private final LiveStreamService liveStream;

    public LiveStreamPublisher(LiveStreamService liveStream) {
        this.liveStream = liveStream;
    }

    @EventListener
    public void onDecision(MidiDecisionEvent event) {
        liveStream.publish(StreamEventType.DECISION, DecisionMessage.from(event.log(), event.status()));
    }
}
