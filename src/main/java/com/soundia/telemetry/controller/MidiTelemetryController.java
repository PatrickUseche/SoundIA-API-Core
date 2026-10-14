package com.soundia.telemetry.controller;

import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.pattern.DjControllerReader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.soundia.telemetry.dto.DjSessionLogDto;
import com.soundia.telemetry.model.DjSessionLog;

@RestController
@RequestMapping("/api/v1/midi")
public class MidiTelemetryController {
    private final DjControllerReader reader;
    
    // Inyeccion de dependencias. Spring arrastra el Sujeto que ya tiene atado el DatabaseMidiObserver
    public MidiTelemetryController(DjControllerReader reader) {
        this.reader = reader;
    }
    
    // El hardware envie el JSON aqui. @RequestBody lo transforma automaticamente a una entidad Java.
    @PostMapping("/event")
    public ResponseEntity<String> receiveMidiEvent(@RequestBody DjSessionLogDto dto){
        // Mapeo manual: Transferimos datos DTO seguro a la Entidad de BD.
        DjSessionLog logEvent = new DjSessionLog();
        logEvent.setControlName(dto.controlName());
        logEvent.setMidiValue(dto.midiValue());
        
        // Asignamos la hora real del servidor, ignorando si el PC del DJ tiene la hora mal configurada.
        logEvent.setTimestamp(System.currentTimeMillis());
        
        // El evento ya validado ingresa a la linea de ensamblaje (BD e IA asincronos)
        reader.notifyObservers(logEvent);
        
        return ResponseEntity.ok("Evento MIDI procesado.");
    }
}