package com.soundia.autodj.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Lo que el DJ eligio como siguiente pista y, si la hay, la que la IA habria propuesto. */
public record ChoiceRequest(
        @NotNull(message = "fromTrackId es obligatorio")
        @Positive(message = "fromTrackId debe ser positivo")
        Long fromTrackId,

        @NotNull(message = "chosenTrackId es obligatorio")
        @Positive(message = "chosenTrackId debe ser positivo")
        Long chosenTrackId,

        @Positive(message = "recommendedTrackId debe ser positivo")
        Long recommendedTrackId
        ) {
}
