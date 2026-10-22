package com.soundia.autodj.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransitionRequest(
        @NotNull(message = "fromTrackId es obligatorio")
        @Positive(message = "fromTrackId debe ser positivo")
        Long fromTrackId,

        @NotNull(message = "toTrackId es obligatorio")
        @Positive(message = "toTrackId debe ser positivo")
        Long toTrackId
        ) {
}
