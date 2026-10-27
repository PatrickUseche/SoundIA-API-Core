package com.soundia.queue.strategy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.soundia.queue.model.QueueItem;

/** Primero en entrar, primero en salir: el orden por defecto. */
@Component
public class FifoOrder implements QueueOrderStrategy {

    public static final String NAME = "fifo";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public QueueItem select(List<QueueItem> queue) {
        return queue.get(0);
    }
}
