package com.soundia.queue.strategy;

import java.util.List;

import org.springframework.stereotype.Component;

import com.soundia.queue.model.QueueItem;

/** La ultima pista encolada sale primero. */
@Component
public class NewestFirstOrder implements QueueOrderStrategy {

    @Override
    public String name() {
        return "newest";
    }

    @Override
    public QueueItem select(List<QueueItem> queue) {
        return queue.get(queue.size() - 1);
    }
}
