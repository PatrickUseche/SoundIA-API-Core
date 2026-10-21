package com.soundia.telemetry.repository;

import com.soundia.telemetry.model.DjSessionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DjSessionLogRepository extends JpaRepository<DjSessionLog, Long>{
    // Retorna solo los ultimos 50 movimientos de la consola DJ.
    List<DjSessionLog> findTop50ByOrderByCreatedInDesc();

    // Eventos de una sesion, del mas antiguo al mas reciente (maximo 500).
    List<DjSessionLog> findTop500BySessionIdOrderByCreatedInAscIdAsc(Long sessionId);
}