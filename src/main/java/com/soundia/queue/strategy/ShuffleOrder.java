package com.soundia.queue.strategy;

import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.soundia.queue.model.QueueItem;

/** Una pista al azar de la cola. */
@Component
public class ShuffleOrder implements QueueOrderStrategy {

    private final Random random;

    public ShuffleOrder() {
        this(new Random());
    }

    /** Para las pruebas: un generador con semilla fija hace el orden predecible. */
    ShuffleOrder(Random random) {
        this.random = random;
    }

    @Override
    public String name() {
        return "shuffle";
    }

    @Override
    public QueueItem select(List<QueueItem> queue) {
        return queue.get(random.nextInt(queue.size()));
    }
}
