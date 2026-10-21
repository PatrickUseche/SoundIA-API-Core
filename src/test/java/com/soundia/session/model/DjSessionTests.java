package com.soundia.session.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class DjSessionTests {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 21, 20, 0);

    @Test
    void unaSesionNuevaEstaActivaYSinFechaDeFin() {
        DjSession sesion = new DjSession("Viernes", INICIO);

        assertTrue(sesion.isActive());
        assertNull(sesion.getEndedAt());
        assertEquals("Viernes", sesion.getName());
        assertEquals(INICIO, sesion.getStartedAt());
    }

    @Test
    void alCerrarseDejaDeEstarActivaYGuardaLaFechaDeFin() {
        DjSession sesion = new DjSession("Viernes", INICIO);

        sesion.end(INICIO.plusHours(2));

        assertFalse(sesion.isActive());
        assertEquals(INICIO.plusHours(2), sesion.getEndedAt());
    }
}
