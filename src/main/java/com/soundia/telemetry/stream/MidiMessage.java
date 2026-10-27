package com.soundia.telemetry.stream;

/** Cuerpo del evento SSE "midi": lo que llego del controlador. */
public record MidiMessage(Integer controlId, Integer value) implements StreamMessage {
}
