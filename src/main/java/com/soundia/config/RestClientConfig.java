package com.soundia.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(IaEngineProperties.class)
public class RestClientConfig {

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

    private static RestTemplate create(IaEngineProperties properties, java.time.Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
