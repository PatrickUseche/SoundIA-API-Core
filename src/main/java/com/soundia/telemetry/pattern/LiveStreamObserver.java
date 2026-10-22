package com.soundia.telemetry.pattern;

import org.springframework.stereotype.Component;

import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.stream.LiveStreamService;
import com.soundia.telemetry.stream.MidiMessage;
import com.soundia.telemetry.stream.StreamEventType;

/** Observador que avisa al navegador, al instante, de cada evento MIDI recibido. */
@Component
public class LiveStreamObserver implements MidiObserver {

    private final LiveStreamService liveStream;

    public LiveStreamObserver(LiveStreamService liveStream) {
        this.liveStream = liveStream;
    }

    @Override
    public void onMidiEvent(DjSessionLog logEvent) {
        liveStream.publish(StreamEventType.MIDI, new MidiMessage(logEvent.getControlId(), logEvent.getValue()));
    }
}
