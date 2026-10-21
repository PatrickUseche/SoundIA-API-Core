package com.soundia.telemetry.stream;

import com.soundia.telemetry.model.DjSessionLog;

/** Evento de aplicacion de Spring: ya se guardo el registro de un evento MIDI. */
public record MidiDecisionEvent(DjSessionLog log, String status) {
}
