package com.soundia.queue.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import com.soundia.queue.model.QueueItem;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class QueueItemRepositoryTests {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 21, 20, 0);

    @Autowired
    private QueueItemRepository repository;

    private QueueItem agregar(long sesion, long pista) {
        return repository.saveAndFlush(new QueueItem(sesion, pista, AHORA));
    }

    @Test
    void laColaDeUnaSesionSigueElOrdenDeInsercion() {
        agregar(1, 30);
        agregar(1, 10);
        agregar(1, 20);

        List<QueueItem> cola = repository.findBySessionIdOrderByIdAsc(1L);

        assertEquals(List.of(30L, 10L, 20L), cola.stream().map(QueueItem::getTrackId).toList());
    }

    @Test
    void cadaSesionTieneSuPropiaCola() {
        agregar(1, 10);
        agregar(2, 20);

        assertEquals(List.of(10L), repository.findBySessionIdOrderByIdAsc(1L).stream().map(QueueItem::getTrackId).toList());
        assertEquals(List.of(20L), repository.findBySessionIdOrderByIdAsc(2L).stream().map(QueueItem::getTrackId).toList());
    }

    @Test
    void laSiguienteEsLaMasAntiguaDeLaSesion() {
        agregar(2, 99);
        agregar(1, 10);
        agregar(1, 20);

        assertEquals(10L, repository.findFirstBySessionIdOrderByIdAsc(1L).orElseThrow().getTrackId());
    }

    @Test
    void unaColaVaciaNoTieneSiguiente() {
        assertTrue(repository.findFirstBySessionIdOrderByIdAsc(1L).isEmpty());
    }

    @Test
    void cuentaLosElementosDeLaSesion() {
        agregar(1, 10);
        agregar(1, 20);
        agregar(2, 30);

        assertEquals(2, repository.countBySessionId(1L));
        assertEquals(0, repository.countBySessionId(9L));
    }

    @Test
    void buscaUnElementoSoloDentroDeSuSesion() {
        QueueItem item = agregar(1, 10);

        assertTrue(repository.findByIdAndSessionId(item.getId(), 1L).isPresent());
        assertTrue(repository.findByIdAndSessionId(item.getId(), 2L).isEmpty());
    }

    @Test
    void persisteLosDatosDelElemento() {
        QueueItem item = agregar(4, 77);

        QueueItem leido = repository.findById(item.getId()).orElseThrow();

        assertEquals(4L, leido.getSessionId());
        assertEquals(77L, leido.getTrackId());
        assertEquals(AHORA, leido.getAddedAt());
    }
}
