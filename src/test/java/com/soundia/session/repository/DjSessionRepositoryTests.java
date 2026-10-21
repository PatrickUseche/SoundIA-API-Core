package com.soundia.session.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import com.soundia.session.model.DjSession;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DjSessionRepositoryTests {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 21, 20, 0);

    @Autowired
    private DjSessionRepository repository;

    private DjSession guardar(String nombre, LocalDateTime inicio, LocalDateTime fin) {
        DjSession sesion = new DjSession(nombre, inicio);
        if (fin != null) {
            sesion.end(fin);
        }
        return repository.saveAndFlush(sesion);
    }

    @Test
    void guardaYLeeUnaSesion() {
        DjSession guardada = guardar("Viernes", BASE, null);

        DjSession leida = repository.findById(guardada.getId()).orElseThrow();

        assertEquals("Viernes", leida.getName());
        assertEquals(BASE, leida.getStartedAt());
        assertTrue(leida.isActive());
    }

    @Test
    void encuentraLaSesionAbierta() {
        guardar("Cerrada", BASE, BASE.plusHours(1));
        DjSession abierta = guardar("Abierta", BASE.plusHours(2), null);

        assertEquals(abierta.getId(), repository.findFirstByEndedAtIsNullOrderByStartedAtDesc().orElseThrow().getId());
    }

    @Test
    void sinSesionAbiertaNoDevuelveNada() {
        guardar("Cerrada", BASE, BASE.plusHours(1));

        assertTrue(repository.findFirstByEndedAtIsNullOrderByStartedAtDesc().isEmpty());
    }

    @Test
    void listaLasMasRecientesPrimero() {
        guardar("vieja", BASE, BASE.plusHours(1));
        guardar("nueva", BASE.plusDays(1), BASE.plusDays(1).plusHours(1));

        List<DjSession> sesiones = repository.findTop20ByOrderByStartedAtDesc();

        assertEquals(List.of("nueva", "vieja"), sesiones.stream().map(DjSession::getName).toList());
    }

    @Test
    void limitaElListadoA20() {
        for (int i = 0; i < 25; i++) {
            guardar("sesion " + i, BASE.plusMinutes(i), BASE.plusMinutes(i).plusSeconds(30));
        }

        assertEquals(20, repository.findTop20ByOrderByStartedAtDesc().size());
    }
}
