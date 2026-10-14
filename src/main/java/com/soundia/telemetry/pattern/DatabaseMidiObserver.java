package com.soundia.telemetry.pattern;

import com.soundia.telemetry.model.DjSessionLog;
import com.soundia.telemetry.repository.DjSessionLogRepository;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMidiObserver implements MidiObserver{
    private final DjSessionLogRepository repository;
    
    // INYECCION POR CONSTRUCTOR: Previene NullPointerException y facilita pruebas unitarias.
    public DatabaseMidiObserver(DjSessionLogRepository repository){
        this.repository = repository;
    }
    
    @Override
    public void onMidiEvent(DjSessionLog logEvent){
        // Persistencia directa. El batching de Hibernate agrupara las consultas hacia PostgreSQL.
        repository.save(logEvent);
    }
}