package com.soundia.telemetry.controller;

import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.pattern.DjControllerReader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<String> receiveMidiEvent(@RequestBody DjSessionLog logEvent){
        // Ingresa el evento a la linea de ensamblaje. Notificara a la BD (y futuramente a la IA) asincronamente.
        reader.notifyObservers(logEvent);
        
        // Retorno a 200 OK casi inmediato para liberar el proceso del hardware DJ.
        return ResponseEntity.ok("Evento MIDI procesado");
    }
}