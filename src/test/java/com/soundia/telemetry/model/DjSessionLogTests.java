package com.soundia.telemetry.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DjSessionLogTests {

    @Test
    void sinDecisionDeIaLaAccionEsNula() {
        DjSessionLog log = new DjSessionLog(14, 127);

        assertNull(log.getAction());
    }

    @Test
    void conservaLaAccionDecididaPorLaIa() {
        DjSessionLog log = new DjSessionLog(14, 127, "Corte de EQ en deck 14");

        assertEquals(14, log.getControlId());
        assertEquals(127, log.getValue());
        assertEquals("Corte de EQ en deck 14", log.getAction());
    }

    @Test
    void laFechaSeAsignaAlPersistir() {
        DjSessionLog log = new DjSessionLog(1, 1);

        // @PrePersist es protected; se invoca por reflexion para no depender de una base de datos.
        try {
            var onCreate = DjSessionLog.class.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            onCreate.invoke(log);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }

        assertNotNull(log.getCreatedIn());
    }

    @Test
    void sinSesionElSessionIdEsNulo() {
        assertNull(new DjSessionLog(14, 127).getSessionId());
    }

    @Test
    void conservaLaSesionAsignada() {
        DjSessionLog log = new DjSessionLog(14, 127);

        log.setSessionId(3L);

        assertEquals(3L, log.getSessionId());
    }

    @Test
    void elBuilderArmaUnRegistroCompleto() {
        DjSessionLog log = DjSessionLog.builder()
                .controlId(14).value(127).action("Corte de EQ en deck 14").sessionId(3L).build();

        assertEquals(14, log.getControlId());
        assertEquals(127, log.getValue());
        assertEquals("Corte de EQ en deck 14", log.getAction());
        assertEquals(3L, log.getSessionId());
    }

    @Test
    void elBuilderDejaVaciosLosCamposOpcionales() {
        DjSessionLog log = DjSessionLog.builder().controlId(1).value(2).build();

        assertNull(log.getAction());
        assertNull(log.getSessionId());
    }

    @Test
    void elBuilderExigeControlYValor() {
        assertThrows(IllegalStateException.class, () -> DjSessionLog.builder().value(2).build());
        assertThrows(IllegalStateException.class, () -> DjSessionLog.builder().controlId(1).build());
    }
}
