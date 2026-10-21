package com.soundia.telemetry.pattern;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.soundia.telemetry.model.DjSessionLog;

class DjControllerReaderTests {

    @Test
    void notificaATodosLosObservadoresRegistrados() {
        MidiObserver primero = mock(MidiObserver.class);
        MidiObserver segundo = mock(MidiObserver.class);
        DjControllerReader reader = new DjControllerReader(List.of(primero, segundo));
        DjSessionLog evento = new DjSessionLog(14, 127);

        reader.notifyObservers(evento);

        verify(primero).onMidiEvent(evento);
        verify(segundo).onMidiEvent(evento);
    }

    @Test
    void notificaAlObservadorAgregadoConAttach() {
        DjControllerReader reader = new DjControllerReader(List.of());
        MidiObserver observador = mock(MidiObserver.class);
        DjSessionLog evento = new DjSessionLog(1, 2);

        reader.attach(observador);
        reader.notifyObservers(evento);

        verify(observador).onMidiEvent(evento);
    }
}
