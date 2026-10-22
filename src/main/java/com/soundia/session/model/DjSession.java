package com.soundia.session.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una sesion de DJ: agrupa los eventos MIDI desde que empieza hasta que se cierra. */
@Entity
@Table(name = "dj_sessions")
public class DjSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    // null mientras la sesion sigue abierta.
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public DjSession() {}

    public DjSession(String name, LocalDateTime startedAt) {
        this.name = name;
        this.startedAt = startedAt;
    }

    public boolean isActive() {
        return endedAt == null;
    }

    /** Cierra la sesion. */
    public void end(LocalDateTime when) {
        this.endedAt = when;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }
}
