package com.soundia.telemetry.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.soundia.config.IaEngineProperties;
import com.soundia.engine.EngineStatus;
import com.soundia.telemetry.dto.AiDecisionDto;
import com.soundia.telemetry.dto.DjSessionLogDto;

/**
 * Unico punto de comunicacion con el motor de IA (Django). Los errores de red se propagan como
 * RestClientException para que quien lo use decida si reintenta.
 */
@Component
public class IaEngineClient {

    private final RestTemplate restTemplate;
    private final IaEngineProperties properties;

    public IaEngineClient(RestTemplate restTemplate, IaEngineProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public AiDecisionDto calculateMix(Integer controlId, Integer value) {
        // Cabeceras explicitas para deshabilitar el envio por fragmentos (chunked): Django no los lee.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<DjSessionLogDto> request = new HttpEntity<>(new DjSessionLogDto(controlId, value), headers);

        EngineStatus status = EngineStatus.getInstance();
        try {
            AiDecisionDto decision = restTemplate.postForObject(properties.mixCalculateUrl(), request, AiDecisionDto.class);
            status.recordSuccess();
            return decision;
        } catch (RestClientException e) {
            status.recordFailure(e.getMessage());
            throw e;
        }
    }
}
