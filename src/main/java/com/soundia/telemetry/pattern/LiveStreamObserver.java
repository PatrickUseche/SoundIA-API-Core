package com.soundia.telemetry.pattern;

import org.springframework.stereotype.Component;

import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.stream.LiveStreamService;
import com.soundia.telemetry.stream.StreamEventType;
import com.soundia.telemetry.stream.StreamMessageFactory;

/** Observador que avisa al navegador, al instante, de cada evento MIDI recibido. */
@Component
public class LiveStreamObserver implements MidiObserver {

    private final LiveStreamService liveStream;
    private final StreamMessageFactory messages;

    public LiveStreamObserver(LiveStreamService liveStream, StreamMessageFactory messages) {
        this.liveStream = liveStream;
        this.messages = messages;
    }

    @Override
    public void onMidiEvent(DjSessionLog logEvent) {
        liveStream.publish(StreamEventType.MIDI, messages.create(StreamEventType.MIDI, logEvent));
    }
}
