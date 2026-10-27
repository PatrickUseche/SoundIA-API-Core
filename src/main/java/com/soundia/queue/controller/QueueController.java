package com.soundia.queue.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.soundia.queue.dto.AddToQueueRequest;
import com.soundia.queue.dto.QueueItemResponse;
import com.soundia.queue.service.QueueService;

import jakarta.validation.Valid;

/** Cola de reproduccion de una sesion de DJ. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/queue")
public class QueueController {

    private final QueueService queue;

    public QueueController(QueueService queue) {
        this.queue = queue;
    }

    @PostMapping
    public ResponseEntity<QueueItemResponse> add(@PathVariable Long sessionId, @Valid @RequestBody AddToQueueRequest request) {
        QueueItemResponse added = QueueItemResponse.from(queue.add(sessionId, request.trackId()));
        return ResponseEntity.created(URI.create("/api/v1/sessions/" + sessionId + "/queue/" + added.id())).body(added);
    }

    @GetMapping
    public List<QueueItemResponse> list(@PathVariable Long sessionId) {
        return queue.list(sessionId).stream().map(QueueItemResponse::from).toList();
    }

    /**
     * Saca de la cola la pista que toca reproducir y la devuelve; 404 si la cola esta vacia. `order` elige
     * la estrategia (fifo, newest, shuffle); sin indicarlo es fifo y un nombre desconocido da 400.
     */
    @PostMapping("/next")
    public QueueItemResponse next(@PathVariable Long sessionId, @RequestParam(required = false) String order) {
        return QueueItemResponse.from(queue.pollNext(sessionId, order));
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> remove(@PathVariable Long sessionId, @PathVariable Long itemId) {
        queue.remove(sessionId, itemId);
        return ResponseEntity.noContent().build();
    }
}
