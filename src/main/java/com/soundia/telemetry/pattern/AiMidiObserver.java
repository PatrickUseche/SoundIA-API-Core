package com.soundia.telemetry.pattern;

import com.soundia.telemetry.model.DjSessionLog;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;

@Component
public class AiMidiObserver implements MidiObserver{
    
    private final RestTemplate restTemplate;
    // Puerto 8000 es el estandar por defecto al levantar servidores en Python (Django/FastAPI).
    private final String pythonAiUrl = "http://localhost:8000/api/mix/calculate";

    public AiMidiObserver() {
        this.restTemplate = new RestTemplate();
    }
    
    @Async
    @Retryable(
            value = { RestClientException.class }, // Solo reintenta si es error de red.
            maxAttempts = 3, // Maximo 3 intentos.
            backoff = @Backoff(delay = 2000) // Espera 2 segundos entre cada intento.
    )
    @Override
    public void onMidiEvent(DjSessionLog logEvent){
        // Forzar cabeceras explicitas para deshabilitar el envio por fragmentos (chunked)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Envolver el evento y las cabeceras en un paquete sellado.
        HttpEntity<DjSessionLog> requestEntity = new HttpEntity<>(logEvent, headers);
        
        // Enviar el paquete estructurado.
        ResponseEntity<String> response = restTemplate.postForEntity(pythonAiUrl, requestEntity, String.class);
        System.out.println("[Motor IA] Transicion calculada por Python: " + response.getBody());
    }
    
    @Recover
    public void recover(RestClientException e, DjSessionLog logEvent){
        System.err.println("[Motor IA] Abortado tras 3 reintentos. Django no responde para el control: " + logEvent.getControlId());
    }
}