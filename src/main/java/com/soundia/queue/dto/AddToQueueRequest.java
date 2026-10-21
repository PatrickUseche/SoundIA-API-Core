package com.soundia.queue.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddToQueueRequest(
        @NotNull(message = "trackId es obligatorio")
        @Positive(message = "trackId debe ser positivo")
        Long trackId
        ) {
}
