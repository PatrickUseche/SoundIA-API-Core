package com.soundia.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracion del motor de IA (Django). Se sobreescribe con soundia.ia-engine.* o con las
 * variables de entorno equivalentes (SOUNDIA_IA_ENGINE_BASE_URL, ...).
 */
@ConfigurationProperties(prefix = "soundia.ia-engine")
public record IaEngineProperties(
        // Puerto 8000 es el estandar por defecto al levantar servidores en Python (Django/FastAPI).
        @DefaultValue("http://localhost:8000") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout
        ) {

    /** Endpoint del motor de IA que calcula la transicion de mezcla. */
    public String mixCalculateUrl() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/api/mix/calculate";
    }
}
