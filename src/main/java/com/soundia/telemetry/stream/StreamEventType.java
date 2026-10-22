package com.soundia.telemetry.stream;

/** Nombres de los eventos SSE que el frontend escucha con EventSource.addEventListener. */
public final class StreamEventType {

    /** Un evento MIDI acaba de llegar del controlador. */
    public static final String MIDI = "midi";

    /** La IA ya calculo la decision para un evento y se guardo. */
    public static final String DECISION = "decision";

    private StreamEventType() {
    }
}
