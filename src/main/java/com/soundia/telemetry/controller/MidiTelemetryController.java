package com.soundia.telemetry.controller;

import com.soundia.telemetry.pattern.DjControllerReader;
import com.soundia.telemetry.service.MidiEventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.dto.DjSessionLogDto;
import com.soundia.telemetry.repository.DjSessionLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.List;
import com.soundia.telemetry.dto.AiDecisionDto;

@RestController
@RequestMapping("/api/v1/midi")
public class MidiTelemetryController {
    private final DjControllerReader reader;
    private final DjSessionLogRepository logRepository;
    private final MidiEventService midiEventService;
    
    // Inyeccion de dependencias. Spring arrastra el Sujeto con sus observadores ya suscritos.
    public MidiTelemetryController(DjControllerReader reader, DjSessionLogRepository logRepository,
                                   MidiEventService midiEventService) {
        this.reader = reader;
        this.logRepository = logRepository;
        this.midiEventService = midiEventService;
    }
    
    // El hardware envie el JSON aqui. @RequestBody lo transforma automaticamente a una entidad Java.
    @PostMapping("/event")
    public ResponseEntity<String> receiveMidiEvent(@Valid @RequestBody DjSessionLogDto dto){
        // Mapeo manual: Transferimos datos DTO seguro a la Entidad de BD.
        DjSessionLog logEvent = new DjSessionLog();
        logEvent.setControlId(dto.controlId());
        logEvent.setValue(dto.value());
        
        // El evento ya validado se procesa de forma asincrona (IA + guardado en BD).
        reader.notifyObservers(logEvent);
        
        return ResponseEntity.ok("Evento MIDI procesado.");
    }
    
    @GetMapping("/logs")
    public List<DjSessionLog> getRecentTelemetry(){
        return logRepository.findTop50ByOrderByCreatedInDesc();
    }
    
    // Version sincrona del mismo flujo: calcula la decision, la guarda y la devuelve al cliente.
    @PostMapping("/ai/predict")
    public AiDecisionDto predict(@Valid @RequestBody DjSessionLogDto eventDto){
        return midiEventService.decideAndStore(eventDto.controlId(), eventDto.value());
    }
}
