package com.soundia.autodj.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** El DJ pidio mezclar antes de lo que el Auto-DJ habia planeado; los momentos son segundos de la pista saliente. */
public record MixTimingRequest(
        @NotNull(message = "fromTrackId es obligatorio")
        @Positive(message = "fromTrackId debe ser positivo")
        Long fromTrackId,

        @NotNull(message = "toTrackId es obligatorio")
        @Positive(message = "toTrackId debe ser positivo")
        Long toTrackId,

        @NotNull(message = "plannedMixOutAt es obligatorio")
        @PositiveOrZero(message = "plannedMixOutAt no puede ser negativo")
        Double plannedMixOutAt,

        @NotNull(message = "actualMixOutAt es obligatorio")
        @PositiveOrZero(message = "actualMixOutAt no puede ser negativo")
        Double actualMixOutAt
        ) {
}
