package com.soundia.session.dto;

import java.time.LocalDateTime;

import com.soundia.session.model.DjSession;

/** Lo que el API expone de una sesion (sin acoplar a los clientes con la entidad JPA). */
public record SessionResponse(
        Long id,
        String name,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        boolean active
        ) {

    public static SessionResponse from(DjSession session) {
        return new SessionResponse(
                session.getId(), session.getName(), session.getStartedAt(), session.getEndedAt(), session.isActive());
    }
}
