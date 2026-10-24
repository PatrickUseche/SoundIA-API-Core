package com.soundia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.sun.net.httpserver.HttpServer;

class RestClientConfigTests {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(RestClientConfig.class);

    private HttpServer server;
    private final java.util.concurrent.atomic.AtomicInteger peticiones = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger fallosPendientes = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.List<String> metodos = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile int statusDeFallo = 502;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/rapido", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/inestable", exchange -> {
            peticiones.incrementAndGet();
            metodos.add(exchange.getRequestMethod());
            if (fallosPendientes.getAndDecrement() > 0) {
                exchange.sendResponseHeaders(statusDeFallo, -1);
            } else {
                byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.createContext("/clave", exchange -> {
            String recibida = exchange.getRequestHeaders().getFirst("X-Soundia-Key");
            byte[] body = String.valueOf(recibida).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/lento", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url(String path) {
        return "http://localhost:" + server.getAddress().getPort() + path;
    }

    @Test
    void usaLosValoresPorDefectoDelMotorDeIa() {
        contextRunner.run(context -> {
            IaEngineProperties props = context.getBean(IaEngineProperties.class);

            assertEquals("http://localhost:8000/api/mix/calculate", props.mixCalculateUrl());
            assertEquals(Duration.ofSeconds(2), props.connectTimeout());
            assertEquals(Duration.ofSeconds(5), props.readTimeout());
            assertEquals(Duration.ofSeconds(120), props.analysisTimeout());
        });
    }

    @Test
    void construyeLaUrlDeLosEndpointsDeAudio() {
        contextRunner.withPropertyValues("soundia.ia-engine.base-url=http://ia.interno:9000/")
                .run(context -> assertEquals("http://ia.interno:9000/api/audio/analyze",
                        context.getBean(IaEngineProperties.class).audioUrl("analyze")));
    }

    @Test
    void construyeLaUrlDeLosEndpointsDelMotorDeMezcla() {
        contextRunner.withPropertyValues("soundia.ia-engine.base-url=http://ia.interno:9000")
                .run(context -> assertEquals("http://ia.interno:9000/api/mix/next",
                        context.getBean(IaEngineProperties.class).mixUrl("next")));
    }

    @Test
    void elTimeoutDeAnalisisSeConfiguraPorPropiedad() {
        contextRunner.withPropertyValues("soundia.ia-engine.analysis-timeout=45s")
                .run(context -> assertEquals(Duration.ofSeconds(45),
                        context.getBean(IaEngineProperties.class).analysisTimeout()));
    }

    @Test
    void laUrlDeDjangoSeConfiguraPorPropiedad() {
        contextRunner.withPropertyValues("soundia.ia-engine.base-url=http://ia.interno:9000/")
                .run(context -> assertEquals("http://ia.interno:9000/api/mix/calculate",
                        context.getBean(IaEngineProperties.class).mixCalculateUrl()));
    }

    private static final IaEngineProperties PROPS =
            new IaEngineProperties("http://localhost", Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(5));

    private RestTemplate conReintentosRapidos() {
        RestClientConfig config = new RestClientConfig();
        config.setRetryDelay(Duration.ofMillis(5));
        return config.iaEngineRestTemplate(PROPS);
    }

    @Test
    void unaConsultaQueRecibe502SeReintentaHastaQueElMotorResponde() {
        fallosPendientes.set(2);

        assertEquals("ok", conReintentosRapidos().getForObject(url("/inestable"), String.class));
        assertEquals(3, peticiones.get());
    }

    @Test
    void con503y504tambienReintenta() {
        for (int status : new int[] {503, 504}) {
            peticiones.set(0);
            fallosPendientes.set(1);
            statusDeFallo = status;

            assertEquals("ok", conReintentosRapidos().getForObject(url("/inestable"), String.class));
            assertEquals(2, peticiones.get(), "codigo " + status);
        }
    }

    @Test
    void siElMotorNoSeRecuperaDejaDeReintentarYPropagaElError() {
        fallosPendientes.set(100);

        assertThrows(org.springframework.web.client.HttpServerErrorException.class,
                () -> conReintentosRapidos().getForObject(url("/inestable"), String.class));
        assertEquals(RestClientConfig.MAX_ATTEMPTS, peticiones.get());
    }

    @Test
    void unErrorDelCliente404NoSeReintenta() {
        fallosPendientes.set(100);
        statusDeFallo = 404;

        assertThrows(org.springframework.web.client.HttpClientErrorException.class,
                () -> conReintentosRapidos().getForObject(url("/inestable"), String.class));
        assertEquals(1, peticiones.get());
    }

    @Test
    void unPostNoSeReintentaPorqueRepetirloPodriaDuplicarAlgo() {
        fallosPendientes.set(1);

        assertThrows(org.springframework.web.client.HttpServerErrorException.class,
                () -> conReintentosRapidos().postForObject(url("/inestable"), "x", String.class));
        assertEquals(1, peticiones.get());
        assertEquals(java.util.List.of("POST"), metodos);
    }

    @Test
    void unPutYUnDeleteSiSeReintentan() {
        fallosPendientes.set(1);
        conReintentosRapidos().put(url("/inestable"), "x");
        assertEquals(java.util.List.of("PUT", "PUT"), metodos);

        metodos.clear();
        fallosPendientes.set(1);
        conReintentosRapidos().delete(url("/inestable"));
        assertEquals(java.util.List.of("DELETE", "DELETE"), metodos);
    }

    @Test
    void elReintentoSigueMandandoLaClaveDeServicio() {
        RestClientConfig config = new RestClientConfig();
        config.setRetryDelay(Duration.ofMillis(5));
        config.setIaEngineKey("clave-larga");
        fallosPendientes.set(1);
        // La ruta /clave devuelve la cabecera que recibe: tras un fallo sigue llegando.
        server.removeContext("/inestable");
        java.util.concurrent.atomic.AtomicInteger intentos = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<String> claves = new java.util.concurrent.CopyOnWriteArrayList<>();
        server.createContext("/inestable", exchange -> {
            claves.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Soundia-Key")));
            if (intentos.getAndIncrement() == 0) {
                exchange.sendResponseHeaders(502, -1);
            } else {
                byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });

        assertEquals("ok", config.iaEngineRestTemplate(PROPS).getForObject(url("/inestable"), String.class));
        assertEquals(java.util.List.of("clave-larga", "clave-larga"), claves);
    }

    @Test
    void sinClaveConfiguradaNoEnviaLaCabecera() {
        assertEquals("null", new RestClientConfig().iaEngineRestTemplate(PROPS).getForObject(url("/clave"), String.class));
    }

    @Test
    void conClaveConfiguradaLaEnviaEnLaCabeceraDeAmbosRestTemplates() {
        RestClientConfig config = new RestClientConfig();
        config.setIaEngineKey("  clave-larga ");

        assertEquals("clave-larga", config.iaEngineRestTemplate(PROPS).getForObject(url("/clave"), String.class));
        assertEquals("clave-larga", config.iaEngineAnalysisRestTemplate(PROPS).getForObject(url("/clave"), String.class));
    }

    @Test
    void unaClaveEnBlancoNoEnviaLaCabecera() {
        RestClientConfig config = new RestClientConfig();
        config.setIaEngineKey("   ");

        assertEquals("null", config.iaEngineRestTemplate(PROPS).getForObject(url("/clave"), String.class));
    }

    @Test
    void respondeNormalmenteSiElServidorEsRapido() {
        RestTemplate restTemplate = new RestClientConfig().iaEngineRestTemplate(
                new IaEngineProperties("http://localhost", Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(60)));

        assertEquals("ok", restTemplate.getForObject(url("/rapido"), String.class));
    }

    @Test
    void cortaLaPeticionCuandoSuperaElTimeoutDeLectura() {
        RestTemplate restTemplate = new RestClientConfig().iaEngineRestTemplate(
                new IaEngineProperties("http://localhost", Duration.ofMillis(300), Duration.ofMillis(300), Duration.ofSeconds(60)));

        long inicio = System.nanoTime();
        assertThrows(ResourceAccessException.class,
                () -> restTemplate.getForObject(url("/lento"), String.class));

        long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        assertTrue(ms < 1400, "debio cortar antes de que responda el servidor lento, tardo " + ms + " ms");
    }

    @Test
    void elRestTemplateDeAnalisisAguantaUnServidorLentoQueElNormalCorta() {
        IaEngineProperties props = new IaEngineProperties(
                "http://localhost", Duration.ofSeconds(1), Duration.ofMillis(300), Duration.ofSeconds(5));
        RestClientConfig config = new RestClientConfig();
        config.setRetryDelay(Duration.ofMillis(5)); // el servidor cierra sin responder: se reintenta, pero sin esperar de verdad

        assertThrows(ResourceAccessException.class,
                () -> config.iaEngineRestTemplate(props).getForObject(url("/lento"), String.class));
        // El servidor lento cierra sin responder: con mas tiempo ya no es un timeout sino otro tipo de fallo.
        long inicio = System.nanoTime();
        try {
            config.iaEngineAnalysisRestTemplate(props).getForObject(url("/lento"), String.class);
        } catch (RuntimeException esperado) {
            // da igual el tipo; lo importante es cuanto espero antes de rendirse
        }
        long ms = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        assertTrue(ms >= 1200, "el RestTemplate de analisis debio esperar la respuesta del servidor lento, espero " + ms + " ms");
    }
}
