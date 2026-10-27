package com.soundia.queue.strategy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.soundia.config.BadRequestException;

/** Las estrategias de orden disponibles, por nombre. Spring entrega todas las que sean `@Component`. */
@Component
public class QueueOrders {

    private final Map<String, QueueOrderStrategy> byName;

    public QueueOrders(List<QueueOrderStrategy> strategies) {
        this.byName = strategies.stream().collect(Collectors.toUnmodifiableMap(QueueOrderStrategy::name, Function.identity()));
    }

    /** La estrategia con ese nombre; FIFO si no se indica ninguno; 400 si no existe. */
    public QueueOrderStrategy get(String name) {
        if (name == null || name.isBlank()) {
            return byName.get(FifoOrder.NAME);
        }
        QueueOrderStrategy strategy = byName.get(name.trim().toLowerCase());
        if (strategy == null) {
            throw new BadRequestException("Orden desconocido '" + name + "'. Opciones: " + String.join(", ", byName.keySet().stream().sorted().toList()) + ".");
        }
        return strategy;
    }
}
