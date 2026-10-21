package com.soundia.telemetry.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import com.soundia.telemetry.model.DjSessionLog;

// replace = NONE: se usa la H2 en modo PostgreSQL del perfil test; la generica trata "value" como reservada.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DjSessionLogRepositoryTests {

    @Autowired
    private DjSessionLogRepository repository;

    @Test
    void guardaLaAccionDeLaIaYAsignaLaFecha() {
        DjSessionLog guardado = repository.saveAndFlush(new DjSessionLog(14, 127, "Corte de EQ en deck 14"));

        DjSessionLog leido = repository.findById(guardado.getId()).orElseThrow();
        assertEquals("Corte de EQ en deck 14", leido.getAction());
        assertNotNull(leido.getCreatedIn());
    }

    @Test
    void permiteGuardarEventosSinDecisionDeIa() {
        DjSessionLog guardado = repository.saveAndFlush(new DjSessionLog(14, 127));

        assertNull(repository.findById(guardado.getId()).orElseThrow().getAction());
    }

    @Test
    void devuelveLosMasRecientesPrimero() throws InterruptedException {
        for (int valor = 1; valor <= 3; valor++) {
            repository.saveAndFlush(new DjSessionLog(1, valor));
            Thread.sleep(5); // separa las marcas de tiempo para que el orden sea determinista
        }

        List<DjSessionLog> logs = repository.findTop50ByOrderByCreatedInDesc();

        assertEquals(List.of(3, 2, 1), logs.stream().map(DjSessionLog::getValue).toList());
    }

    @Test
    void limitaElHistorialALosUltimos50() {
        for (int i = 0; i < 55; i++) {
            repository.save(new DjSessionLog(1, i % 128));
        }
        repository.flush();

        assertEquals(50, repository.findTop50ByOrderByCreatedInDesc().size());
    }
}
