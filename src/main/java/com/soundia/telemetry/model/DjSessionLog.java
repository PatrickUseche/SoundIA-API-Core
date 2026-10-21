package com.soundia.telemetry.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "dj_session_logs")
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
}