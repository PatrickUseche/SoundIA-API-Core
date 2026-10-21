package com.soundia;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.retry.annotation.EnableRetry;
// Al estar anidado en "soundia.soundia", Spring Boot ignorará la carpeta "telemetry". 
// Debes forzar el escaneo apuntando a la raíz "com.soundia".
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableRetry
public class SoundiaApplication {
    public static void main(String[] args) {
        SpringApplication.run(SoundiaApplication.class, args);
    }
}