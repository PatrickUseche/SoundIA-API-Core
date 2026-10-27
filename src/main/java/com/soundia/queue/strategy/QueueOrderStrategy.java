package com.soundia.queue.strategy;

import java.util.List;

import com.soundia.queue.model.QueueItem;

/**
 * Patron Strategy: decide cual de las pistas encoladas toca reproducir. Cada implementacion es un algoritmo
 * intercambiable; la cola no sabe cual se usa. Una estrategia nueva solo tiene que ser un `@Component`.
 */
public interface QueueOrderStrategy {

    /** Nombre con el que el cliente la elige (`?order=`), en minusculas. */
    String name();

    /**
     * La pista que toca reproducir.
     *
     * @param queue cola de la sesion en orden de insercion; nunca esta vacia
     */
    QueueItem select(List<QueueItem> queue);
}
