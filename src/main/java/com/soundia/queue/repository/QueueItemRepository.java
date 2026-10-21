package com.soundia.queue.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.soundia.queue.model.QueueItem;

public interface QueueItemRepository extends JpaRepository<QueueItem, Long> {

    /** La cola de una sesion en orden de reproduccion (el mas antiguo primero). */
    List<QueueItem> findBySessionIdOrderByIdAsc(Long sessionId);

    /** La siguiente pista que toca reproducir. */
    Optional<QueueItem> findFirstBySessionIdOrderByIdAsc(Long sessionId);

    Optional<QueueItem> findByIdAndSessionId(Long id, Long sessionId);

    long countBySessionId(Long sessionId);
}
