package com.soundia.queue.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Una pista en la cola de reproduccion de una sesion. El orden de la cola es el orden de insercion
 * (el id crece), asi que no hace falta una columna de posicion.
 */
@Entity
@Table(name = "queue_items", indexes = @Index(name = "idx_queue_items_session", columnList = "session_id"))
public class QueueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    // Id de la pista en el motor de IA (Django es el dueno de los datos de pista).
    @Column(name = "track_id", nullable = false, updatable = false)
    private Long trackId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    public QueueItem() {}

    public QueueItem(Long sessionId, Long trackId, LocalDateTime addedAt) {
        this.sessionId = sessionId;
        this.trackId = trackId;
        this.addedAt = addedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getTrackId() {
        return trackId;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }
}
