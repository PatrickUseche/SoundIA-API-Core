package com.soundia.telemetry.repository;

import com.soundia.telemetry.model.DjSessionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DjSessionLogRepository extends JpaRepository<DjSessionLog, Long>{
    // Retorna solo los ultimos 50 movimientos de la consola DJ.
    List<DjSessionLog> findTop50ByOrderByCreatedAtDesc();
}