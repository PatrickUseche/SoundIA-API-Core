package com.soundia.telemetry.pattern;

import com.soundia.telemetry.model.DjSessionLog;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DjControllerReader{
    private final List<MidiObserver> observers = new CopyOnWriteArrayList<>();

    // Spring entrega todos los MidiObserver registrados como beans: un observador nuevo
    // (por ejemplo WebSockets) se suscribe solo con ser un @Component, sin tocar esta clase.
    public DjControllerReader(List<MidiObserver> registeredObservers) {
        this.observers.addAll(registeredObservers);
    }

    public void attach(MidiObserver observer){
        this.observers.add(observer);
    }
    
    // Se ejecuta cuando el hardware envia una senal MIDI; cada observador decide si es asincrono.
    public void notifyObservers(DjSessionLog logEvent){
        for(MidiObserver observer : observers){
            observer.onMidiEvent(logEvent);
        }
    }
}
