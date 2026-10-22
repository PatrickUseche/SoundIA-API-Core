package com.soundia.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Limita los intentos de login fallidos por origen (la direccion de la conexion): pasados `maxAttempts` fallos
 * dentro de `window`, se bloquea ese origen hasta que el fallo mas antiguo salga de la ventana. Un login correcto
 * borra los fallos. Esta en memoria: se reinicia con la aplicacion, lo que basta para un unico usuario.
 */
public class LoginThrottle {

    // Tope de origenes recordados: evita que alguien llene la memoria probando desde miles de direcciones.
    private static final int MAX_TRACKED_SOURCES = 10_000;

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new HashMap<>();

    public LoginThrottle(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** Segundos que faltan para poder reintentar; 0 si el origen no esta bloqueado. */
    public synchronized long secondsUntilAllowed(String source) {
        Deque<Instant> recent = recentFailures(source);
        if (recent.size() < maxAttempts) {
            return 0;
        }
        Instant unblocksAt = recent.peekFirst().plus(window);
        long seconds = Duration.between(clock.instant(), unblocksAt).toSeconds() + 1;
        return Math.max(seconds, 1);
    }

    public synchronized void recordFailure(String source) {
        if (!failures.containsKey(source) && failures.size() >= MAX_TRACKED_SOURCES) {
            forgetExpired();
            if (failures.size() >= MAX_TRACKED_SOURCES) {
                return; // sin sitio para otro origen: mejor no recordarlo que agotar la memoria
            }
        }
        failures.computeIfAbsent(source, key -> new ArrayDeque<>()).addLast(clock.instant());
    }

    public synchronized void recordSuccess(String source) {
        failures.remove(source);
    }

    private Deque<Instant> recentFailures(String source) {
        Deque<Instant> deque = failures.get(source);
        if (deque == null) {
            return new ArrayDeque<>();
        }
        Instant limit = clock.instant().minus(window);
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(limit)) {
            deque.removeFirst();
        }
        if (deque.isEmpty()) {
            failures.remove(source);
        }
        return deque;
    }

    private void forgetExpired() {
        Instant limit = clock.instant().minus(window);
        failures.values().removeIf(deque -> deque.isEmpty() || !deque.peekLast().isAfter(limit));
    }
}
