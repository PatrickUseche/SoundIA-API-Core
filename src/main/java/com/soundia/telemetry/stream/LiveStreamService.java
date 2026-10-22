package com.soundia.telemetry.stream;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Mantiene los clientes conectados por SSE y les reparte los eventos en vivo. Un cliente que se
 * desconecta o falla se elimina solo; el navegador reconecta con EventSource.
 */
@Service
public class LiveStreamService {

    private static final Logger log = LoggerFactory.getLogger(LiveStreamService.class);

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final long timeoutMillis;

    public LiveStreamService(@Value("${soundia.stream.timeout:30m}") Duration timeout) {
        this.timeoutMillis = timeout.toMillis();
    }

    /** Registra un cliente nuevo y le avisa que la conexion quedo abierta. */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    /** Envia un evento con nombre y cuerpo JSON a todos los clientes conectados. */
    public void publish(String eventName, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                // El cliente cerro la conexion: se descarta sin ensuciar los logs.
                emitters.remove(emitter);
                log.debug("Cliente SSE descartado: {}", e.getMessage());
            }
        }
    }

    /**
     * Comentario SSE periodico (no es un evento): evita que proxies o el navegador cierren por
     * inactividad una conexion abierta y permite detectar clientes caidos.
     */
    @Scheduled(fixedRateString = "${soundia.stream.heartbeat:15s}")
    public void sendHeartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                log.debug("Cliente SSE descartado en el latido: {}", e.getMessage());
            }
        }
    }

    public int connectedClients() {
        return emitters.size();
    }
}
