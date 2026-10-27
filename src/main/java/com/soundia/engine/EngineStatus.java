package com.soundia.engine;

import java.time.Clock;
import java.time.Instant;

/**
 * Patron Singleton: el estado del motor de IA (Django) segun las ultimas llamadas que se le hicieron. En la nube
 * gratuita el motor se duerme y tarda cerca de un minuto en despertar, asi que saber si responde importa; como el
 * estado es uno solo para toda la aplicacion, todos los clientes que hablan con el motor lo comparten.
 *
 * La instancia se crea de forma perezosa con el idiom del holder (la JVM garantiza que se inicializa una sola vez y
 * sin bloqueos al leerla). Las lecturas y escrituras son `synchronized`, porque las llamadas al motor llegan desde
 * varios hilos (peticiones HTTP y observadores asincronos).
 */
public final class EngineStatus {

    /** Lo que se sabe del motor. */
    public enum State {
        /** Aun no se le ha hecho ninguna llamada. */
        UNKNOWN,
        /** La ultima llamada funciono. */
        UP,
        /** La ultima llamada fallo. */
        DOWN
    }

    /** Foto del estado en un instante; es inmutable. */
    public record Snapshot(State state, int consecutiveFailures, Instant lastSuccessAt, Instant lastFailureAt,
                           String lastError) {
    }

    private static final class Holder {
        private static final EngineStatus INSTANCE = new EngineStatus(Clock.systemUTC());
    }

    /** La unica instancia de la aplicacion. */
    public static EngineStatus getInstance() {
        return Holder.INSTANCE;
    }

    private final Clock clock;
    private State state = State.UNKNOWN;
    private int consecutiveFailures;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
    private String lastError;

    // Privado: nadie fuera de esta clase crea instancias. El constructor con reloj existe para las pruebas.
    private EngineStatus(Clock clock) {
        this.clock = clock;
    }

    /** Una instancia aparte con reloj propio, solo para pruebas; la aplicacion usa {@link #getInstance()}. */
    static EngineStatus forTesting(Clock clock) {
        return new EngineStatus(clock);
    }

    /** El motor respondio bien. */
    public synchronized void recordSuccess() {
        state = State.UP;
        consecutiveFailures = 0;
        lastSuccessAt = clock.instant();
    }

    /** La llamada al motor fallo; `reason` es un resumen del error. */
    public synchronized void recordFailure(String reason) {
        state = State.DOWN;
        consecutiveFailures++;
        lastFailureAt = clock.instant();
        lastError = reason;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(state, consecutiveFailures, lastSuccessAt, lastFailureAt, lastError);
    }

    /** Vuelve al estado inicial; lo usan las pruebas para no depender del orden en que se ejecutan. */
    public synchronized void reset() {
        state = State.UNKNOWN;
        consecutiveFailures = 0;
        lastSuccessAt = null;
        lastFailureAt = null;
        lastError = null;
    }
}
