package com.soundia.telemetry.pattern;

import com.soundia.telemetry.model.DjSessionLog;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;

@Component
public class AiMidiObserver implements MidiObserver{
    
    private final RestTemplate restTemplate;
    // Puerto 8000 es el estandar por defecto al levantar servidores en Python (Django/FastAPI).
    private final String pythonAiUrl = "http://localhost:8000/api/mix/calculate";

    public AiMidiObserver() {
        this.restTemplate = new RestTemplate();
    }
    
    @Async
    @Override
    public void onMidiEvent(DjSessionLog logEvent){
        try{
            // Spring Boot toma el objeto logEvent, lo serializa a JSON internamente,
            // y hace un POST hacia la URL del motor en Python.
            ResponseEntity<String> response = restTemplate.postForEntity(pythonAiUrl, logEvent, String.class);
            System.out.println("[Motor IA] Transicion calculada por Python: " + response.getBody());    
        } catch (RestClientException e){
            // Si el servidor de Python esta apagado, el error se atrapa aqui sin tumbar la aplicacion Java.
            System.err.println("[Motor IA] Advertencia - Motor Python inalcanzable: " + e.getMessage());
        }
    }
}