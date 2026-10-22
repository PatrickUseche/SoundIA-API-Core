package com.soundia.session.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.soundia.config.ConflictException;
import com.soundia.config.ResourceNotFoundException;
import com.soundia.session.model.DjSession;
import com.soundia.session.repository.DjSessionRepository;

class DjSessionServiceTests {

    private static final Instant AHORA = Instant.parse("2026-10-21T20:00:00Z");

    private DjSessionRepository repository;
    private DjSessionService service;

    @BeforeEach
    void setUp() {
        repository = mock(DjSessionRepository.class);
        when(repository.save(any(DjSession.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        service = new DjSessionService(repository, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private DjSession sesion(long id, boolean cerrada) {
        DjSession sesion = new DjSession("Sesion " + id, LocalDateTime.of(2026, 10, 21, 18, 0));
        sesion.setId(id);
        if (cerrada) {
            sesion.end(LocalDateTime.of(2026, 10, 21, 19, 0));
        }
        return sesion;
    }

    @Test
    void iniciaUnaSesionConLaHoraActual() {
        when(repository.findFirstByEndedAtIsNullOrderByStartedAtDesc()).thenReturn(Optional.empty());

        DjSession iniciada = service.start("Viernes");

        assertEquals("Viernes", iniciada.getName());
        assertEquals(LocalDateTime.of(2026, 10, 21, 20, 0), iniciada.getStartedAt());
        assertTrue(iniciada.isActive());
    }

    @Test
    void recortaLosEspaciosDelNombre() {
        when(repository.findFirstByEndedAtIsNullOrderByStartedAtDesc()).thenReturn(Optional.empty());

        assertEquals("Viernes", service.start("  Viernes  ").getName());
    }

    @Test
    void noPermiteIniciarSiHayUnaSesionAbierta() {
        when(repository.findFirstByEndedAtIsNullOrderByStartedAtDesc()).thenReturn(Optional.of(sesion(3, false)));

        ConflictException error = assertThrows(ConflictException.class, () -> service.start("Otra"));

        assertTrue(error.getMessage().contains("id 3"), error.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void cierraUnaSesionAbiertaConLaHoraActual() {
        when(repository.findById(5L)).thenReturn(Optional.of(sesion(5, false)));

        DjSession cerrada = service.end(5L);

        assertFalse(cerrada.isActive());
        assertEquals(LocalDateTime.of(2026, 10, 21, 20, 0), cerrada.getEndedAt());
    }

    @Test
    void noPermiteCerrarDosVecesLaMismaSesion() {
        when(repository.findById(5L)).thenReturn(Optional.of(sesion(5, true)));

        assertThrows(ConflictException.class, () -> service.end(5L));
        verify(repository, never()).save(any());
    }

    @Test
    void cerrarUnaSesionInexistenteDa404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.end(9L));
    }

    @Test
    void obtenerUnaSesionInexistenteDa404() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(9L));
    }

    @Test
    void devuelveLaSesionActiva() {
        DjSession abierta = sesion(4, false);
        when(repository.findFirstByEndedAtIsNullOrderByStartedAtDesc()).thenReturn(Optional.of(abierta));

        assertEquals(abierta, service.getActive());
        assertEquals(4L, service.activeSessionId());
    }

    @Test
    void sinSesionActivaGetActiveDa404YElIdEsNulo() {
        when(repository.findFirstByEndedAtIsNullOrderByStartedAtDesc()).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getActive());
        assertNull(service.activeSessionId());
    }

    @Test
    void listaLasSesionesRecientes() {
        List<DjSession> recientes = List.of(sesion(2, true), sesion(1, true));
        when(repository.findTop20ByOrderByStartedAtDesc()).thenReturn(recientes);

        assertEquals(recientes, service.recent());
    }
}
