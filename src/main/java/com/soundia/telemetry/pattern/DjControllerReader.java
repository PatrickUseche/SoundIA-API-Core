package com.soundia.telemetry.pattern;

import com.soundia.telemetry.model.DjSessionLog;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Component
public class DjControllerReader{
    private final List<MidiObserver> observers = new ArrayList<>();
    private final DatabaseMidiObserver databaseMidiObserver;

    public DjControllerReader(DatabaseMidiObserver databaseMidiObserver) {
        this.databaseMidiObserver = databaseMidiObserver;
    }
    
    @PostConstruct
    public void init(){
        // Vincula el observador de BD al Sujeto en el momento en que Spring Boot arranca.
        this.attach(databaseMidiObserver);
    }
    
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