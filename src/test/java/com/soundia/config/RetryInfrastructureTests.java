package com.soundia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClientException;

/**
 * Verifica que la infraestructura de reintentos (spring-retry + AOP) funciona con la version
 * de Spring Framework que trae Spring Boot. No necesita base de datos.
 */
class RetryInfrastructureTests {

    @Configuration
    @EnableRetry
    static class Config {
        @Bean
        FlakyService flakyService() {
            return new FlakyService();
        }
    }

    static class FlakyService {
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger recovered = new AtomicInteger();
        private int failuresBeforeSuccess;

        // El bean se envuelve en un proxy AOP, asi que el estado se consulta con metodos y no con campos.
        public int attempts() {
            return attempts.get();
        }

        public int recovered() {
            return recovered.get();
        }

        public void failTimes(int failures) {
            this.failuresBeforeSuccess = failures;
        }

        @Retryable(retryFor = RestClientException.class, maxAttempts = 3, backoff = @Backoff(delay = 1))
        public String call() {
            if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
                throw new RestClientException("fallo simulado");
            }
            return "ok";
        }

        @Recover
        public String recover(RestClientException e) {
            recovered.incrementAndGet();
            return "recuperado";
        }
    }

    private FlakyService serviceWithFailures(AnnotationConfigApplicationContext ctx, int failures) {
        FlakyService service = ctx.getBean(FlakyService.class);
        service.failTimes(failures);
        return service;
    }

    @Test
    void reintentaHastaTenerExito() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            FlakyService service = serviceWithFailures(ctx, 2);

            assertEquals("ok", service.call());
            assertEquals(3, service.attempts());
            assertEquals(0, service.recovered());
        }
    }

    @Test
    void invocaRecoverTrasAgotarLosIntentos() {
        try (var ctx = new AnnotationConfigApplicationContext(Config.class)) {
            FlakyService service = serviceWithFailures(ctx, 10);

            assertEquals("recuperado", service.call());
            assertEquals(3, service.attempts());
            assertEquals(1, service.recovered());
        }
    }
}
