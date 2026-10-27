package com.soundia.queue.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

import com.soundia.config.BadRequestException;
import com.soundia.config.ConflictException;
import com.soundia.config.ResourceNotFoundException;
import com.soundia.queue.model.QueueItem;
import com.soundia.queue.repository.QueueItemRepository;
import com.soundia.queue.strategy.FifoOrder;
import com.soundia.queue.strategy.NewestFirstOrder;
import com.soundia.queue.strategy.QueueOrders;
import com.soundia.queue.strategy.ShuffleOrder;
import com.soundia.session.model.DjSession;
import com.soundia.session.service.DjSessionService;
import com.soundia.track.TrackClient;

class QueueServiceTests {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 21, 18, 0);

    private QueueItemRepository repository;
    private DjSessionService sessions;
    private TrackClient tracks;
    private QueueService service;

    @BeforeEach
    void setUp() {
        repository = mock(QueueItemRepository.class);
        sessions = mock(DjSessionService.class);
        tracks = mock(TrackClient.class);
        when(repository.save(any(QueueItem.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        service = new QueueService(repository, sessions, tracks,
                Clock.fixed(Instant.parse("2026-10-21T20:00:00Z"), ZoneOffset.UTC),
                new QueueOrders(List.of(new FifoOrder(), new NewestFirstOrder(), new ShuffleOrder())));
    }

    private DjSession sesion(long id, boolean cerrada) {
        DjSession sesion = new DjSession("Sesion", INICIO);
        sesion.setId(id);
        if (cerrada) {
            sesion.end(INICIO.plusHours(1));
        }
        return sesion;
    }

    private void sesionAbierta(long id) {
        when(sessions.get(id)).thenReturn(sesion(id, false));
    }

    private QueueItem item(long id, long sesion, long pista) {
        QueueItem item = new QueueItem(sesion, pista, INICIO);
        item.setId(id);
        return item;
    }

    @Test
    void alBorrarUnaPistaLaQuitaDeTodasLasColas() {
        service.removeTrack(10L);

        verify(repository).deleteByTrackId(10L);
    }

    @Test
    void agregaLaPistaAlFinalDeLaColaConLaHoraActual() {
        sesionAbierta(5);

        QueueItem agregado = service.add(5L, 42L);

        assertEquals(5L, agregado.getSessionId());
        assertEquals(42L, agregado.getTrackId());
        assertEquals(LocalDateTime.of(2026, 10, 21, 20, 0), agregado.getAddedAt());
    }

    @Test
    void verificaQueLaPistaExisteAntesDeGuardar() {
        sesionAbierta(5);

        service.add(5L, 42L);

        var orden = inOrder(tracks, repository);
        orden.verify(tracks).getTrack(42L);
        orden.verify(repository).save(any(QueueItem.class));
    }

    @Test
    void siLaPistaNoExisteNoSeGuardaNada() {
        sesionAbierta(5);
        doThrow(new ResourceAccessException("Django caido")).when(tracks).getTrack(42L);

        assertThrows(ResourceAccessException.class, () -> service.add(5L, 42L));

        verify(repository, never()).save(any());
    }

    @Test
    void noPermiteEncolarEnUnaSesionCerrada() {
        when(sessions.get(5L)).thenReturn(sesion(5, true));

        ConflictException error = assertThrows(ConflictException.class, () -> service.add(5L, 42L));

        assertTrue(error.getMessage().contains("cerrada"), error.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void encolarEnUnaSesionInexistenteDa404() {
        when(sessions.get(9L)).thenThrow(new ResourceNotFoundException("No existe la sesion 9."));

        assertThrows(ResourceNotFoundException.class, () -> service.add(9L, 42L));
    }

    @Test
    void noPermiteSuperarElTopeDeLaCola() {
        sesionAbierta(5);
        when(repository.countBySessionId(5L)).thenReturn((long) QueueService.MAX_QUEUE_SIZE);

        assertThrows(ConflictException.class, () -> service.add(5L, 42L));

        verify(repository, never()).save(any());
    }

    @Test
    void conUnLugarLibreTodaviaPermiteEncolar() {
        sesionAbierta(5);
        when(repository.countBySessionId(5L)).thenReturn((long) QueueService.MAX_QUEUE_SIZE - 1);

        service.add(5L, 42L);

        verify(repository).save(any(QueueItem.class));
    }

    @Test
    void listaLaColaEnOrden() {
        sesionAbierta(5);
        List<QueueItem> cola = List.of(item(1, 5, 10), item(2, 5, 20));
        when(repository.findBySessionIdOrderByIdAsc(5L)).thenReturn(cola);

        assertEquals(cola, service.list(5L));
    }

    @Test
    void listarLaColaDeUnaSesionInexistenteDa404() {
        when(sessions.get(9L)).thenThrow(new ResourceNotFoundException("No existe la sesion 9."));

        assertThrows(ResourceNotFoundException.class, () -> service.list(9L));
    }

    @Test
    void quitaUnElementoDeLaCola() {
        sesionAbierta(5);
        QueueItem elemento = item(3, 5, 10);
        when(repository.findByIdAndSessionId(3L, 5L)).thenReturn(Optional.of(elemento));

        service.remove(5L, 3L);

        verify(repository).delete(elemento);
    }

    @Test
    void quitarUnElementoQueNoEstaEnEsaSesionDa404() {
        sesionAbierta(5);
        when(repository.findByIdAndSessionId(3L, 5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.remove(5L, 3L));
        verify(repository, never()).delete(any());
    }

    @Test
    void sacaLaSiguientePistaYLaQuitaDeLaCola() {
        sesionAbierta(5);
        QueueItem primero = item(1, 5, 10);
        when(repository.findBySessionIdOrderByIdAsc(5L)).thenReturn(List.of(primero, item(2, 5, 20)));

        QueueItem siguiente = service.pollNext(5L);

        assertSame(primero, siguiente);
        ArgumentCaptor<QueueItem> borrado = ArgumentCaptor.forClass(QueueItem.class);
        verify(repository).delete(borrado.capture());
        assertSame(primero, borrado.getValue());
    }

    @Test
    void sacarDeUnaColaVaciaDa404ConUnMensajeClaro() {
        sesionAbierta(5);
        when(repository.findBySessionIdOrderByIdAsc(5L)).thenReturn(List.of());

        ResourceNotFoundException error = assertThrows(ResourceNotFoundException.class, () -> service.pollNext(5L));

        assertTrue(error.getMessage().contains("vacia"), error.getMessage());
        verify(repository, never()).delete(any());
    }

    @Test
    void conOrderNewestSacaLaUltimaPistaEncolada() {
        sesionAbierta(5);
        QueueItem primero = item(1, 5, 10);
        QueueItem ultimo = item(2, 5, 20);
        when(repository.findBySessionIdOrderByIdAsc(5L)).thenReturn(List.of(primero, ultimo));

        QueueItem siguiente = service.pollNext(5L, "newest");

        assertSame(ultimo, siguiente);
        verify(repository).delete(ultimo);
    }

    @Test
    void elNombreDelOrdenNoDistingueMayusculas() {
        sesionAbierta(5);
        QueueItem ultimo = item(2, 5, 20);
        when(repository.findBySessionIdOrderByIdAsc(5L)).thenReturn(List.of(item(1, 5, 10), ultimo));

        assertSame(ultimo, service.pollNext(5L, " NEWEST "));
    }

    @Test
    void sinIndicarOrdenUsaFifo() {
        sesionAbierta(5);
        QueueItem primero = item(1, 5, 10);
        when(repository.findBySessionIdOrderByIdAsc(5L)).thenReturn(List.of(primero, item(2, 5, 20)));

        assertSame(primero, service.pollNext(5L, null));
        assertSame(primero, service.pollNext(5L, " "));
    }

    @Test
    void unOrdenDesconocidoDa400SinTocarLaCola() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> service.pollNext(5L, "alfabetico"));

        assertTrue(error.getMessage().contains("alfabetico"), error.getMessage());
        assertTrue(error.getMessage().contains("fifo, newest, shuffle"), error.getMessage());
        verify(repository, never()).delete(any());
    }
}
