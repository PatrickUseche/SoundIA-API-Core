package com.soundia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Spring limita por defecto los archivos subidos a 1 MB; una pista de audio pesa varios MB, asi que el
 * limite tiene que estar configurado o toda subida real fallaria con 413.
 */
@SpringJUnitConfig(classes = MultipartPropertiesTests.Vacia.class,
        initializers = ConfigDataApplicationContextInitializer.class)
class MultipartPropertiesTests {

    static class Vacia {
    }

    @Autowired
    private Environment env;

    @Test
    void permiteArchivosDeHasta50MB() {
        assertEquals("50MB", env.getProperty("spring.servlet.multipart.max-file-size"));
    }

    @Test
    void laPeticionPuedeSerUnPocoMayorQueElArchivo() {
        assertEquals("55MB", env.getProperty("spring.servlet.multipart.max-request-size"));
    }
}
