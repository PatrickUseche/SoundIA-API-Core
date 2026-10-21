package com.soundia.session.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soundia.session.dto.SessionResponse;
import com.soundia.session.dto.StartSessionRequest;
import com.soundia.session.service.DjSessionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sessions")
public class DjSessionController {

    private final DjSessionService sessions;

    public DjSessionController(DjSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> start(@Valid @RequestBody StartSessionRequest request) {
        SessionResponse created = SessionResponse.from(sessions.start(request.name()));
        return ResponseEntity.created(URI.create("/api/v1/sessions/" + created.id())).body(created);
    }

    @GetMapping
    public List<SessionResponse> recent() {
        return sessions.recent().stream().map(SessionResponse::from).toList();
    }

    @GetMapping("/active")
    public SessionResponse active() {
        return SessionResponse.from(sessions.getActive());
    }

    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable Long id) {
        return SessionResponse.from(sessions.get(id));
    }

    @PostMapping("/{id}/end")
    public SessionResponse end(@PathVariable Long id) {
        return SessionResponse.from(sessions.end(id));
    }
}
