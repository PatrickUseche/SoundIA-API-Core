package com.soundia.autodj.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Valoracion del DJ de una transicion: si le gusto o no. */
public record FeedbackRequest(
        @NotNull(message = "fromTrackId es obligatorio")
        @Positive(message = "fromTrackId debe ser positivo")
        Long fromTrackId,

        @NotNull(message = "toTrackId es obligatorio")
        @Positive(message = "toTrackId debe ser positivo")
        Long toTrackId,

        @NotNull(message = "liked es obligatorio")
        Boolean liked
        ) {
}
