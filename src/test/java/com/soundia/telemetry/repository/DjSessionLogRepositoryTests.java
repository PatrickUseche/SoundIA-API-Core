package com.soundia.telemetry.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.soundia.telemetry.model.DjSessionLog;

// replace = NONE: se usa la H2 en modo PostgreSQL del perfil test; la generica trata "value" como reservada.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DjSessionLogRepositoryTests {

    @Autowired
    private DjSessionLogRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

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

    private DjSessionLog enSesion(long sesion, int valor) {
        DjSessionLog log = new DjSessionLog(1, valor);
        log.setSessionId(sesion);
        return repository.saveAndFlush(log);
    }

    @Test
    void devuelveSoloLosEventosDeLaSesionPedida() {
        enSesion(1, 10);
        enSesion(2, 20);
        enSesion(1, 11);

        List<DjSessionLog> logs = repository.findTop500BySessionIdOrderByCreatedInAscIdAsc(1L);

        assertEquals(List.of(10, 11), logs.stream().map(DjSessionLog::getValue).toList());
    }

    @Test
    void losEventosDeUnaSesionVanDelMasAntiguoAlMasReciente() throws InterruptedException {
        for (int valor = 1; valor <= 3; valor++) {
            enSesion(5, valor);
            Thread.sleep(5);
        }

        List<DjSessionLog> logs = repository.findTop500BySessionIdOrderByCreatedInAscIdAsc(5L);

        assertEquals(List.of(1, 2, 3), logs.stream().map(DjSessionLog::getValue).toList());
    }

    @Test
    void unaSesionSinEventosDevuelveListaVacia() {
        assertEquals(0, repository.findTop500BySessionIdOrderByCreatedInAscIdAsc(99L).size());
    }

    @Test
    void persisteElSessionId() {
        DjSessionLog guardado = enSesion(8, 1);

        assertEquals(8L, repository.findById(guardado.getId()).orElseThrow().getSessionId());
    }

    @Test
    void laTablaTieneLosIndicesQueUsanLasConsultasDelRepositorio() {
        assertEquals(List.of("created_in"), columnasDelIndice("idx_dj_session_logs_created_in"));
        // El orden importa: primero la sesion (filtro) y luego la fecha (orden dentro de la sesion).
        assertEquals(List.of("session_id", "created_in"), columnasDelIndice("idx_dj_session_logs_session_created"));
    }

    private List<String> columnasDelIndice(String indice) {
        return jdbc.queryForList(
                "SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                        + "WHERE LOWER(INDEX_NAME) = ? ORDER BY ORDINAL_POSITION",
                String.class, indice);
    }
}
