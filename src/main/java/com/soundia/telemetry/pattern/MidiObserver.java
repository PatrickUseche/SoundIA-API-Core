package com.soundia.telemetry.pattern;

import com.soundia.telemetry.model.DjSessionLog;

public interface MidiObserver{
    // CONTRATO ESTRICTO: Todo observador (BD, IA o WebSockets) debe manejar este evento.
    void onMidiEvent(DjSessionLog logEvent);
}