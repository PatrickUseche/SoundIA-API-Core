package com.soundia.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(IaEngineProperties.class)
public class RestClientConfig {

    /** Cabecera con la clave que el motor de IA exige cuando es accesible desde Internet (SERVICE_TOKEN en Django). */
    static final String KEY_HEADER = "X-Soundia-Key";

    // Vacia = el motor no exige clave (red privada o desarrollo). Se define con SOUNDIA_IA_ENGINE_KEY.
    @Value("${soundia.ia-engine.key:}")
    private String iaEngineKey;

    void setIaEngineKey(String iaEngineKey) {
        this.iaEngineKey = iaEngineKey;
    }

    // Con timeouts: sin ellos, un Django colgado bloquearia los hilos del API indefinidamente.
    @Bean
    @Primary
    public RestTemplate iaEngineRestTemplate(IaEngineProperties properties) {
        return create(properties, properties.readTimeout());
    }

    // Para el analisis de audio, que tarda mucho mas que una consulta normal.
    @Bean
    public RestTemplate iaEngineAnalysisRestTemplate(IaEngineProperties properties) {
        return create(properties, properties.analysisTimeout());
    }

    private RestTemplate create(IaEngineProperties properties, java.time.Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(readTimeout);
        // Se guarda el cuerpo en memoria para enviar Content-Length: Django en desarrollo (runserver, WSGI)
        // no lee Transfer-Encoding: chunked, que es lo que enviaria una subida multipart en streaming.
        RestTemplate restTemplate = new RestTemplate(new BufferingClientHttpRequestFactory(factory));
        if (iaEngineKey != null && !iaEngineKey.isBlank()) {
            String key = iaEngineKey.strip();
            ClientHttpRequestInterceptor addKey = (request, body, execution) -> {
                request.getHeaders().set(KEY_HEADER, key);
                return execution.execute(request, body);
            };
            restTemplate.getInterceptors().add(addKey);
        }
        return restTemplate;
    }
}
