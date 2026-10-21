package com.soundia.telemetry.pattern;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.stream.LiveStreamService;
import com.soundia.telemetry.stream.MidiMessage;
import com.soundia.telemetry.stream.StreamEventType;

class LiveStreamObserverTests {

    @Test
    void publicaElEventoMidiRecibidoAlStream() {
        LiveStreamService liveStream = mock(LiveStreamService.class);
        LiveStreamObserver observer = new LiveStreamObserver(liveStream);

        observer.onMidiEvent(new DjSessionLog(14, 127));

        verify(liveStream).publish(StreamEventType.MIDI, new MidiMessage(14, 127));
    }
}
