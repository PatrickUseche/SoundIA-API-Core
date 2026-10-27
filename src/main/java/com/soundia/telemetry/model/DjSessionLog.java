package com.soundia.telemetry.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
// Cada evento MIDI es una fila, asi que la tabla crece sin parar. Sin indices, las dos consultas del repositorio
// recorren toda la tabla (O(N)); con ellos bajan por el arbol (O(log N + k), k = 50 o 500 filas).
@Table(name = "dj_session_logs", indexes = {
        // findTop50ByOrderByCreatedInDesc: los ultimos movimientos de la consola.
        @Index(name = "idx_dj_session_logs_created_in", columnList = "created_in"),
        // findTop500BySessionIdOrderByCreatedInAscIdAsc: filtra por sesion y ya viene ordenado por fecha.
        @Index(name = "idx_dj_session_logs_session_created", columnList = "session_id, created_in")
})
public class DjSessionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "control_id", nullable = false)
    private Integer controlId;

    @Column(name = "value", nullable = false)
    private Integer value;

    // Decision de la IA para este evento. Es null si el motor de IA no estuvo disponible.
    @Column(name = "action")
    private String action;

    // Sesion de DJ abierta cuando llego el evento; null si no habia ninguna.
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "created_in", updatable = false)
    private LocalDateTime createdIn;

    // Se ejecuta automáticamente en RAM justo antes del INSERT
    @PrePersist
    protected void onCreate() {
        this.createdIn = LocalDateTime.now();
    }

    public DjSessionLog() {}

    public DjSessionLog(Integer controlId, Integer value) {
        this.controlId = controlId;
        this.value = value;
    }

    public DjSessionLog(Integer controlId, Integer value, String action) {
        this(controlId, value);
        this.action = action;
    }

    /** Patron Builder: arma un registro con los campos opcionales que haga falta (accion, sesion). */
    public static Builder builder() {
        return new Builder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getControlId() {
        return controlId;
    }

    public void setControlId(Integer controlId) {
        this.controlId = controlId;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public LocalDateTime getCreatedIn() {
        return createdIn;
    }

    public void setCreatedIn(LocalDateTime createdIn) {
        this.createdIn = createdIn;
    }

    /** Construye un {@link DjSessionLog}; el control y el valor son obligatorios, el resto es opcional. */
    public static final class Builder {

        private Integer controlId;
        private Integer value;
        private String action;
        private Long sessionId;

        private Builder() {
        }

        public Builder controlId(Integer controlId) {
            this.controlId = controlId;
            return this;
        }

        public Builder value(Integer value) {
            this.value = value;
            return this;
        }

        /** Decision de la IA; se omite cuando el motor no estuvo disponible. */
        public Builder action(String action) {
            this.action = action;
            return this;
        }

        /** Sesion de DJ abierta cuando llego el evento; se omite si no habia ninguna. */
        public Builder sessionId(Long sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /** @throws IllegalStateException si falta el control o el valor */
        public DjSessionLog build() {
            if (controlId == null || value == null) {
                throw new IllegalStateException("Un registro MIDI necesita controlId y value.");
            }
            DjSessionLog log = new DjSessionLog(controlId, value, action);
            log.setSessionId(sessionId);
            return log;
        }
    }
}
