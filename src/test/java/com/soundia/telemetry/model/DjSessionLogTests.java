package com.soundia.telemetry.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
