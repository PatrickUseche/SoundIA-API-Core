package com.soundia.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    // Reloj inyectable: en las pruebas se reemplaza por uno fijo para no depender de la hora real.
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
