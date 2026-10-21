package com.soundia.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(IaEngineProperties.class)
public class RestClientConfig {

    // Con timeouts: sin ellos, un Django colgado bloquearia los hilos del API indefinidamente.
    @Bean
    public RestTemplate iaEngineRestTemplate(IaEngineProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return new RestTemplate(factory);
    }
}
