package com.soundia.telemetry.stream;

/** Cuerpo de un evento SSE. Cada tipo de evento de {@link StreamEventType} tiene su propio mensaje. */
public sealed interface StreamMessage permits MidiMessage, DecisionMessage {
}
