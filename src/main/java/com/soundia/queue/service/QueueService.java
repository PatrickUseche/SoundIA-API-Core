package com.soundia.queue.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soundia.config.ConflictException;
import com.soundia.config.ResourceNotFoundException;
import com.soundia.queue.model.QueueItem;
import com.soundia.queue.repository.QueueItemRepository;
import com.soundia.session.model.DjSession;
import com.soundia.session.service.DjSessionService;
import com.soundia.track.TrackClient;

/** Cola de reproduccion de una sesion de DJ: las pistas que el Auto-DJ tiene previsto poner, en orden. */
@Service
public class QueueService {

    /** Tope de pistas encoladas por sesion, para que una cola no crezca sin control. */
    public static final int MAX_QUEUE_SIZE = 100;

    private final QueueItemRepository repository;
    private final DjSessionService sessions;
    private final TrackClient tracks;
    private final Clock clock;

    public QueueService(QueueItemRepository repository, DjSessionService sessions, TrackClient tracks, Clock clock) {
        this.repository = repository;
        this.sessions = sessions;
        this.tracks = tracks;
        this.clock = clock;
    }

    /**
     * Agrega una pista al final de la cola. La sesion debe existir y estar abierta, y la pista debe existir
     * en el motor de IA (si no, el motor responde 404 y llega al cliente).
     */
    @Transactional
    public QueueItem add(Long sessionId, Long trackId) {
        DjSession session = sessions.get(sessionId);
        if (!session.isActive()) {
            throw new ConflictException("La sesion " + sessionId + " ya esta cerrada.");
        }
        if (repository.countBySessionId(sessionId) >= MAX_QUEUE_SIZE) {
            throw new ConflictException("La cola de la sesion " + sessionId + " esta llena (maximo " + MAX_QUEUE_SIZE + ").");
        }
        tracks.getTrack(trackId); // verifica que la pista existe
        return repository.save(new QueueItem(sessionId, trackId, LocalDateTime.now(clock)));
    }

    public List<QueueItem> list(Long sessionId) {
        sessions.get(sessionId);
        return repository.findBySessionIdOrderByIdAsc(sessionId);
    }

    @Transactional
    public void remove(Long sessionId, Long itemId) {
        sessions.get(sessionId);
        QueueItem item = repository.findByIdAndSessionId(itemId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe el elemento " + itemId + " en la cola de la sesion " + sessionId + "."));
        repository.delete(item);
    }

    /** Saca de la cola la pista que toca reproducir y la devuelve. */
    @Transactional
    public QueueItem pollNext(Long sessionId) {
        sessions.get(sessionId);
        QueueItem next = repository.findFirstBySessionIdOrderByIdAsc(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("La cola de la sesion " + sessionId + " esta vacia."));
        repository.delete(next);
        return next;
    }
}
