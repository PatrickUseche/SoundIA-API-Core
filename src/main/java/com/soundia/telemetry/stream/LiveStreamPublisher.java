package com.soundia.telemetry.stream;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Traduce los eventos internos de la aplicacion a mensajes SSE para el navegador. */
@Component
public class LiveStreamPublisher {

    private final LiveStreamService liveStream;
    private final StreamMessageFactory messages;

    public LiveStreamPublisher(LiveStreamService liveStream, StreamMessageFactory messages) {
        this.liveStream = liveStream;
        this.messages = messages;
    }

    @EventListener
    public void onDecision(MidiDecisionEvent event) {
        liveStream.publish(StreamEventType.DECISION, messages.create(StreamEventType.DECISION, event.log(), event.status()));
    }
}
