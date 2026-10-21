package com.soundia.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Las credenciales de PostgreSQL deben venir del entorno y no estar escritas en
 * application.properties.
 */
@SpringJUnitConfig(classes = DatasourcePropertiesTests.Vacia.class,
        initializers = ConfigDataApplicationContextInitializer.class)
@TestPropertySource(properties = {
        "DB_URL=jdbc:postgresql://host-de-prueba:5432/otra_db",
        "DB_USERNAME=usuario_de_prueba",
        "DB_PASSWORD=clave-de-prueba"
})
class DatasourcePropertiesTests {

    static class Vacia {
    }

    @Autowired
    private Environment env;

    @Test
    void laUrlSeLeeDeLaVariableDeEntorno() {
        assertEquals("jdbc:postgresql://host-de-prueba:5432/otra_db", env.getProperty("spring.datasource.url"));
    }

    @Test
    void elUsuarioSeLeeDeLaVariableDeEntorno() {
        assertEquals("usuario_de_prueba", env.getProperty("spring.datasource.username"));
    }

    @Test
    void laContrasenaSeLeeDeLaVariableDeEntorno() {
        assertEquals("clave-de-prueba", env.getProperty("spring.datasource.password"));
    }
}
