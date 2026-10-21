package com.soundia.session.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soundia.session.model.DjSession;

public interface DjSessionRepository extends JpaRepository<DjSession, Long> {

    /** La sesion abierta, si hay una. */
    Optional<DjSession> findFirstByEndedAtIsNullOrderByStartedAtDesc();

    /** Las 20 sesiones mas recientes. */
    List<DjSession> findTop20ByOrderByStartedAtDesc();
}
