package com.soundia.telemetry.pattern;

import com.soundia.telemetry.model.DjSessionLog;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class DjControllerReader{
    private final List<MidiObserver> observers = new ArrayList<>();
    
    public void attach(MidiObserver observer){
        this.observers.add(observer);
    }
    
    // Se ejecuta asincronamente cuando el hardware envia una senal MIDI.
    public void notifyObserves(DjSessionLog logEvent){
        for(MidiObserver observer : observers){
            observer.onMidiEvent(logEvent);
        }
    }
}