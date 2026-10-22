package com.soundia.session.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soundia.config.ConflictException;
import com.soundia.config.ResourceNotFoundException;
import com.soundia.session.model.DjSession;
import com.soundia.session.repository.DjSessionRepository;

/** Ciclo de vida de las sesiones de DJ. Solo puede haber una sesion abierta a la vez. */
@Service
public class DjSessionService {

    private final DjSessionRepository repository;
    private final Clock clock;

    public DjSessionService(DjSessionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public DjSession start(String name) {
        repository.findFirstByEndedAtIsNullOrderByStartedAtDesc().ifPresent(abierta -> {
            throw new ConflictException(
                    "Ya hay una sesion abierta (id " + abierta.getId() + "). Cierrala antes de iniciar otra.");
        });
        return repository.save(new DjSession(name.trim(), LocalDateTime.now(clock)));
    }

    @Transactional
    public DjSession end(Long id) {
        DjSession session = get(id);
        if (!session.isActive()) {
            throw new ConflictException("La sesion " + id + " ya estaba cerrada.");
        }
        session.end(LocalDateTime.now(clock));
        return repository.save(session);
    }

    public DjSession get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe la sesion " + id + "."));
    }

    public DjSession getActive() {
        return findActive().orElseThrow(() -> new ResourceNotFoundException("No hay ninguna sesion abierta."));
    }

    public Optional<DjSession> findActive() {
        return repository.findFirstByEndedAtIsNullOrderByStartedAtDesc();
    }

    /** Id de la sesion abierta, o null si no hay ninguna (los eventos se guardan igual, sin sesion). */
    public Long activeSessionId() {
        return findActive().map(DjSession::getId).orElse(null);
    }

    public List<DjSession> recent() {
        return repository.findTop20ByOrderByStartedAtDesc();
    }
}
