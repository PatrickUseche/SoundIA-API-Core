package com.soundia.queue.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.soundia.config.BadRequestException;
import com.soundia.queue.model.QueueItem;

class QueueOrderStrategyTests {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 21, 18, 0);

    private final QueueItem a = item(1, 10);
    private final QueueItem b = item(2, 20);
    private final QueueItem c = item(3, 30);
    private final List<QueueItem> cola = List.of(a, b, c);

    private static QueueItem item(long id, long pista) {
        QueueItem item = new QueueItem(5L, pista, INICIO);
        item.setId(id);
        return item;
    }

    @Test
    void fifoEligeLaPrimeraPista() {
        assertSame(a, new FifoOrder().select(cola));
    }

    @Test
    void newestEligeLaUltimaPista() {
        assertSame(c, new NewestFirstOrder().select(cola));
    }

    @Test
    void conUnaSolaPistaTodasLasEstrategiasEligenEsa() {
        List<QueueItem> una = List.of(b);

        assertSame(b, new FifoOrder().select(una));
        assertSame(b, new NewestFirstOrder().select(una));
        assertSame(b, new ShuffleOrder().select(una));
    }

    @Test
    void shuffleSiempreEligeUnaPistaDeLaCola() {
        ShuffleOrder shuffle = new ShuffleOrder(new Random(7));
        Set<QueueItem> elegidas = new HashSet<>();

        for (int i = 0; i < 200; i++) {
            QueueItem elegida = shuffle.select(cola);
            assertTrue(cola.contains(elegida));
            elegidas.add(elegida);
        }

        assertEquals(3, elegidas.size(), "con 200 sorteos deberian salir las tres pistas");
    }

    @Test
    void shuffleConLaMismaSemillaEligeLoMismo() {
        assertSame(new ShuffleOrder(new Random(42)).select(cola), new ShuffleOrder(new Random(42)).select(cola));
    }

    @Test
    void elRegistroResuelveCadaEstrategiaPorNombre() {
        FifoOrder fifo = new FifoOrder();
        NewestFirstOrder newest = new NewestFirstOrder();
        QueueOrders orders = new QueueOrders(List.of(fifo, newest, new ShuffleOrder()));

        assertSame(fifo, orders.get("fifo"));
        assertSame(newest, orders.get("NEWEST"));
        assertEquals("shuffle", orders.get(" shuffle ").name());
    }

    @Test
    void sinNombreElRegistroDevuelveFifo() {
        FifoOrder fifo = new FifoOrder();
        QueueOrders orders = new QueueOrders(List.of(new NewestFirstOrder(), fifo));

        assertSame(fifo, orders.get(null));
        assertSame(fifo, orders.get(""));
    }

    @Test
    void unNombreDesconocidoListaLasOpciones() {
        QueueOrders orders = new QueueOrders(List.of(new FifoOrder(), new NewestFirstOrder(), new ShuffleOrder()));

        BadRequestException error = assertThrows(BadRequestException.class, () -> orders.get("raro"));

        assertTrue(error.getMessage().contains("fifo, newest, shuffle"), error.getMessage());
    }
}
