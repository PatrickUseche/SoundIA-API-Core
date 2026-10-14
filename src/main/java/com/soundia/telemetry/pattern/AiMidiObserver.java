package com.soundia.telemetry.pattern;

import com.soundia.telemetry.model.DjSessionLog;
import org.springframework.stereotype.Component;

@Component
public class AiMidiObserver implements MidiObserver{
    @Override
    public void onMidiEvent(DjSessionLog logEvent){
        // Aqui se conectara el modelo de IA para la mezcla automatica.
        // Por ahora intercepta el dato en tiempo real.
        System.out.println("[Motor IA] Calculando transicion para evento MIDI...");
    }
}